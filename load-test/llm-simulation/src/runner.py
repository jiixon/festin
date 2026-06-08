"""
Festin LLM 유저 에이전트 시뮬레이션 러너

Phase 1 (1명):    에이전트 검증
Phase 2 (14명):   패턴 다양성, 버그 탐지
Phase 3 (100명):  피크 시뮬레이션 (단일 실행, 균등 분포)
Phase 4:          시간대별 파동을 연속으로 — DAU 1000 누적 한 달치 부하 압축 운영

사용법:
  python runner.py --phase 1
  python runner.py --phase 2
  python runner.py --phase 3
  python runner.py --phase 4 --waves 10                 # 점심→오후→저녁 순환 10 파동
  python runner.py --phase 4 --waves 3 --band lunch     # 점심 파동만 3번 (피크 집중)
  python runner.py --phase 4 --waves 1 --band lunch     # GitHub Actions cron (Phase 4b) 단일 파동

환경변수:
  FESTIN_BASE_URL          서버 URL (기본: http://localhost:8080)
  GROQ_API_KEY             Groq API 키
  WAVE_VISITOR_COUNT       파동당 방문객 수 (기본: 50)
  WAVE_ARRIVAL_SPREAD_SEC  시차 도착 펼침 시간 (기본: 180)
"""

import asyncio
import argparse
import json
import math
import random
import time
from datetime import datetime
from pathlib import Path

import aiohttp

from config import (
    PATTERN_DISTRIBUTION,
    TIME_BAND_PATTERNS,
    TIME_BAND_BOOTH_WEIGHTS,
    TIME_BAND_DESCRIPTIONS,
    BANDS_CYCLE,
    WAVE_VISITOR_COUNT,
    WAVE_ARRIVAL_SPREAD_SEC,
    WAVE_MONITOR_INTERVAL_SEC,
    STAFF_BOOTHS,
)
from festin_client import FestinClient
from gemini_client import persona_for
from agent import Agent, AgentState


# ── Phase 1~3 에이전트 구성 ──────────────────────────────────

PHASE_1_AGENTS = [
    {"pattern": "waiter"},
]

PHASE_2_AGENTS = [
    {"pattern": "explorer"},
    {"pattern": "explorer"},
    {"pattern": "explorer"},
    {"pattern": "waiter"},
    {"pattern": "waiter"},
    {"pattern": "leaver"},
    {"pattern": "retrier"},
    {"pattern": "double_tap"},
    {"pattern": "net_retry"},
    {"pattern": "staff", "booth_id": 4},
    {"pattern": "staff", "booth_id": 5},
    {"pattern": "staff", "booth_id": 6},
    {"pattern": "staff", "booth_id": 7},
    {"pattern": "staff", "booth_id": 8},
]


def build_phase_3_agents(n: int = 100) -> list:
    agents = []
    for pattern, ratio in PATTERN_DISTRIBUTION.items():
        count = max(1, int(n * ratio))
        for _ in range(count):
            agents.append({"pattern": pattern})
    return agents[:n]


# ── Phase 4: 시간대별 파동 구성 ──────────────────────────────

def _weighted_pick(weights: dict):
    """가중치 dict에서 키 하나 가중 샘플링."""
    items = list(weights.items())
    total = sum(w for _, w in items)
    r = random.random() * total
    acc = 0.0
    for key, w in items:
        acc += w
        if r <= acc:
            return key
    return items[-1][0]


def build_wave_agents(band: str, count: int = WAVE_VISITOR_COUNT) -> list:
    """시간대별 패턴·부스 가중치 반영해 파동 한 번분 에이전트 구성.

    - 패턴은 TIME_BAND_PATTERNS 가중치로 샘플링
    - concurrent 는 코디네이터가 인기 부스를 지정 (충돌이 자연스럽게 동일 부스에 몰리도록)
    - staff 는 부스마다 1명 고정
    """
    patterns_dist = TIME_BAND_PATTERNS[band]
    booth_weights = TIME_BAND_BOOTH_WEIGHTS[band]

    agents = []
    for _ in range(count):
        pattern = _weighted_pick(patterns_dist)
        cfg = {"pattern": pattern}
        if pattern == "concurrent":
            cfg["booth_id"] = _weighted_pick(booth_weights)
        agents.append(cfg)

    for booth_id in STAFF_BOOTHS:
        agents.append({"pattern": "staff", "booth_id": booth_id})

    return agents


def schedule_arrival_delays(n: int, spread_sec: float) -> list[float]:
    """n명이 spread_sec 동안 푸아송 도착으로 들어오게 한다고 가정한 각 에이전트 시작 지연.

    LLM rate-limit thundering herd 완화 + 현실적인 시차 도착 모사.
    """
    if n == 0 or spread_sec <= 0:
        return [0.0] * n
    rate = n / spread_sec  # 평균 도착률 (명/초)
    delays = []
    t = 0.0
    for _ in range(n):
        gap = random.expovariate(rate)
        t += gap
        delays.append(min(t, spread_sec))  # spread_sec 안으로 클램프
    return delays


# ── 부스 불균형 모니터 (Phase 3 숙제 #3) ─────────────────────

async def monitor_booth_imbalance(
    client: FestinClient,
    token: str,
    stop_event: asyncio.Event,
    snapshots: list,
    interval_sec: float = WAVE_MONITOR_INTERVAL_SEC,
):
    """주기적으로 /booths 폴링 → 부스별 currentWaiting 시계열 누적.

    종료 시 compute_imbalance_metrics 가 stdev/cv 로 변환.
    """
    while not stop_event.is_set():
        res = await client.get_booths(token)
        if res.status == 200:
            raw = res.body
            booths = raw.get("booths", []) if isinstance(raw, dict) else (raw if isinstance(raw, list) else [])
            sample = []
            for b in booths:
                bid = b.get("boothId") or b.get("id")
                if bid is None:
                    continue
                sample.append({"booth_id": bid, "waiting": b.get("currentWaiting", 0)})
            snapshots.append({"t": round(time.monotonic(), 2), "booths": sample})
        try:
            await asyncio.wait_for(stop_event.wait(), timeout=interval_sec)
        except asyncio.TimeoutError:
            pass


def compute_imbalance_metrics(snapshots: list) -> dict:
    """부스 대기열 시계열로 불균형 지표 계산.

    - mean_stdev: 시점별 stdev의 시계열 평균 (절대 규모)
    - max_stdev:  시계열 최대 (가장 불균형했던 순간)
    - mean_cv:    변동계수 평균 (stdev/mean, 규모 무관 비교)
    """
    per_stdev = []
    per_cv = []
    for snap in snapshots:
        vals = [b["waiting"] for b in snap["booths"]]
        if len(vals) < 2:
            continue
        mean = sum(vals) / len(vals)
        var = sum((v - mean) ** 2 for v in vals) / len(vals)
        stdev = math.sqrt(var)
        per_stdev.append(stdev)
        if mean > 0:
            per_cv.append(stdev / mean)
    if not per_stdev:
        return {"snapshots": len(snapshots)}
    return {
        "snapshots": len(snapshots),
        "mean_stdev": round(sum(per_stdev) / len(per_stdev), 2),
        "max_stdev":  round(max(per_stdev), 2),
        "mean_cv":    round(sum(per_cv) / len(per_cv), 3) if per_cv else None,
    }


# ── 파동 1회 실행 ────────────────────────────────────────────

async def run_wave(client: FestinClient, band: str, wave_id: int) -> dict | None:
    print(f"\n{'='*60}")
    print(f"  파동 #{wave_id:04d} — {band}")
    print(f"  {TIME_BAND_DESCRIPTIONS[band]}")
    print(f"  시작: {datetime.now().strftime('%H:%M:%S')}")
    print(f"{'='*60}")

    configs = build_wave_agents(band)

    # 1. 토큰 발급 — 파동별 고유 이메일로 stale Redis key 충돌 회피
    agents_with_cfg = []
    monitor_token = None
    print(f"[Setup] 토큰 발급 중... ({len(configs)}명)")
    for i, cfg in enumerate(configs):
        pattern = cfg["pattern"]
        role = "STAFF" if pattern == "staff" else "VISITOR"
        if pattern == "staff":
            email = f"sim_staff_w{wave_id:04d}_b{cfg['booth_id']}@test.com"
        else:
            email = f"sim_w{wave_id:04d}_{i:03d}_{pattern}@test.com"
        token = await client.login(email, f"SimW{wave_id}-{i}", role=role, booth_id=cfg.get("booth_id"))
        if not token:
            continue
        if monitor_token is None:
            monitor_token = token

        state = AgentState(
            agent_id=f"w{wave_id:04d}_a{i:03d}",
            pattern=pattern,
            persona=persona_for(pattern),
            token=token,
            assigned_booth_id=cfg.get("booth_id"),
        )
        agents_with_cfg.append((Agent(state, client), cfg))

    if not agents_with_cfg:
        print(f"  ⚠️  파동 #{wave_id}: 토큰 발급 실패, 건너뜀")
        return None

    visitors = [a for a, c in agents_with_cfg if c["pattern"] != "staff"]
    staff_agents = [a for a, c in agents_with_cfg if c["pattern"] == "staff"]
    print(f"[Run] 방문객 {len(visitors)}명 (~{WAVE_ARRIVAL_SPREAD_SEC}초 시차 도착) + staff {len(staff_agents)}명")

    # 2. 부스 불균형 모니터 백그라운드 시작
    snapshots: list = []
    stop_event = asyncio.Event()
    monitor_task = asyncio.create_task(
        monitor_booth_imbalance(client, monitor_token, stop_event, snapshots)
    )

    # 3. 방문객 시차 도착 + staff 즉시 시작 → 동시 실행
    delays = schedule_arrival_delays(len(visitors), WAVE_ARRIVAL_SPREAD_SEC)

    async def with_delay(agent: Agent, delay: float):
        if delay > 0:
            await asyncio.sleep(delay)
        await agent.run()

    sim_start = time.monotonic()
    await asyncio.gather(
        *(with_delay(a, d) for a, d in zip(visitors, delays)),
        *(s.run() for s in staff_agents),
    )
    elapsed = time.monotonic() - sim_start

    # 4. 모니터 종료
    stop_event.set()
    await monitor_task

    print(f"\n[Done] 파동 #{wave_id} 완료 ({elapsed:.1f}초)")

    # 5. 집계
    result = collect_results([a for a, _ in agents_with_cfg])
    result["wave_id"] = wave_id
    result["band"] = band
    result["elapsed_sec"] = round(elapsed, 1)
    result["booth_imbalance"] = compute_imbalance_metrics(snapshots)
    return result


# ── 결과 집계 ────────────────────────────────────────────────

def collect_results(agents: list[Agent]) -> dict:
    all_logs = []
    metrics = {
        "total_agents": len(agents),
        "total_browse": 0,
        "total_enqueue": 0,
        "total_cancel": 0,
        "cancel_positions": [],
        "bugs_detected": [],
        "llm_decisions": [],
        "by_pattern": {},
    }

    for a in agents:
        s = a.s
        all_logs.extend(s.action_log)

        metrics["total_browse"] += s.total_browse
        metrics["total_enqueue"] += s.total_enqueue
        metrics["total_cancel"] += s.total_cancel
        metrics["cancel_positions"].extend(s.cancel_positions)

        p = s.pattern
        if p not in metrics["by_pattern"]:
            metrics["by_pattern"][p] = {"count": 0, "enqueue": 0, "cancel": 0}
        metrics["by_pattern"][p]["count"] += 1
        metrics["by_pattern"][p]["enqueue"] += s.total_enqueue
        metrics["by_pattern"][p]["cancel"] += s.total_cancel

        for log in s.action_log:
            if log.get("bug_detected"):
                metrics["bugs_detected"].append(log)
            if log.get("event", "").startswith("llm_decision"):
                metrics["llm_decisions"].append(log)

    enq = metrics["total_enqueue"]
    can = metrics["total_cancel"]

    unique_enrolled = sum(1 for a in agents if a.s.total_enqueue > 0)

    metrics["abandonment_rate"] = round(can / enq, 3) if enq > 0 else 0
    metrics["conversion_rate"] = round(unique_enrolled / len(agents), 3) if agents else 0
    metrics["avg_cancel_position"] = (
        round(sum(metrics["cancel_positions"]) / len(metrics["cancel_positions"]), 1)
        if metrics["cancel_positions"] else None
    )

    return {"logs": all_logs, "metrics": metrics}


# ── 저장 ─────────────────────────────────────────────────────

def _results_dir() -> Path:
    d = Path(__file__).parent / "results"
    d.mkdir(exist_ok=True)
    return d


def save_results(results: dict, phase: int):
    """Phase 1~3 단일 실행 결과."""
    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    path = _results_dir() / f"phase{phase}_{ts}.json"
    with open(path, "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    print(f"[저장] {path}")


def save_wave_result(result: dict):
    """Phase 4 파동 1회 결과 — 매 파동마다 즉시 디스크에 보존 (크래시 시 진행분 손실 방지)."""
    phase4_dir = _results_dir() / "phase4"
    phase4_dir.mkdir(exist_ok=True)
    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    path = phase4_dir / f"wave_{result['wave_id']:04d}_{result['band']}_{ts}.json"
    with open(path, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)
    print(f"  [저장] {path.name}")


def update_cumulative_summary(result: dict):
    """파동 결과를 누적 summary에 반영. Phase 4a 진행상황 추적용."""
    phase4_dir = _results_dir() / "phase4"
    phase4_dir.mkdir(exist_ok=True)
    summary_path = phase4_dir / "summary.json"
    if summary_path.exists():
        with open(summary_path, encoding="utf-8") as f:
            summary = json.load(f)
    else:
        summary = {
            "started_at": datetime.now().isoformat(),
            "total_waves": 0,
            "total_agents": 0,
            "total_enqueue": 0,
            "total_cancel": 0,
            "total_bugs": 0,
            "by_band": {},
            "imbalance": {"mean_stdev_avg": 0.0, "max_stdev_ever": 0.0},
        }

    m = result["metrics"]
    band = result["band"]
    summary["total_waves"] += 1
    summary["total_agents"] += m["total_agents"]
    summary["total_enqueue"] += m["total_enqueue"]
    summary["total_cancel"] += m["total_cancel"]
    summary["total_bugs"] += len(m["bugs_detected"])

    bb = summary["by_band"].setdefault(band, {"waves": 0, "enqueue": 0, "cancel": 0, "bugs": 0})
    bb["waves"] += 1
    bb["enqueue"] += m["total_enqueue"]
    bb["cancel"] += m["total_cancel"]
    bb["bugs"] += len(m["bugs_detected"])

    imb = result.get("booth_imbalance", {})
    if "mean_stdev" in imb:
        n = summary["total_waves"]
        prev = summary["imbalance"]["mean_stdev_avg"]
        summary["imbalance"]["mean_stdev_avg"] = round(
            ((prev * (n - 1)) + imb["mean_stdev"]) / n, 2
        )
        summary["imbalance"]["max_stdev_ever"] = round(
            max(summary["imbalance"]["max_stdev_ever"], imb.get("max_stdev", 0)), 2
        )

    summary["last_updated"] = datetime.now().isoformat()
    with open(summary_path, "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)


# ── 요약 출력 ────────────────────────────────────────────────

def print_summary(results: dict, header: str = "비즈니스 지표 요약"):
    m = results["metrics"]
    print("=" * 60)
    print(f"  {header}")
    print("=" * 60)
    print(f"  총 에이전트:        {m['total_agents']}명")
    print(f"  부스 탐색:          {m['total_browse']}회")
    print(f"  대기 등록:          {m['total_enqueue']}회")
    print(f"  대기 취소:          {m['total_cancel']}회")
    print(f"  이탈률:             {m['abandonment_rate'] * 100:.1f}%")
    print(f"  전환율 (등록자/전체): {m['conversion_rate'] * 100:.1f}%")
    print(f"  평균 이탈 순번:     {m['avg_cancel_position']}")
    print(f"  버그 탐지:          {len(m['bugs_detected'])}건")
    print(f"  LLM 판단 횟수:      {len(m['llm_decisions'])}회")
    if "booth_imbalance" in results:
        imb = results["booth_imbalance"]
        if "mean_stdev" in imb:
            print(f"  부스 불균형(stdev): 평균 {imb['mean_stdev']} / 최대 {imb['max_stdev']} / CV {imb.get('mean_cv')}")

    if m["bugs_detected"]:
        print("\n  [⚠️  버그 상세]")
        for bug in m["bugs_detected"]:
            print(f"    {bug['agent_id']} / {bug['event']} / statuses={bug.get('statuses')}")

    print("=" * 60)


# ── 메인 (Phase 1~3) ─────────────────────────────────────────

async def main_phase_123(phase: int):
    print(f"\n{'='*55}")
    print(f"  Festin LLM 시뮬레이션 — {phase}단계")
    print(f"  시작: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"{'='*55}\n")

    agent_configs = {1: PHASE_1_AGENTS, 2: PHASE_2_AGENTS}.get(phase, build_phase_3_agents(100))

    async with aiohttp.ClientSession(timeout=aiohttp.ClientTimeout(total=15)) as session:
        client = FestinClient(session)

        print(f"[Setup] 토큰 발급 중... ({len(agent_configs)}명)")
        agents = []
        for i, cfg in enumerate(agent_configs):
            agent_id = f"agent_{i+1:03d}"
            pattern = cfg["pattern"]
            role = "STAFF" if pattern == "staff" else "VISITOR"
            email = f"sim_{agent_id}_{pattern}@test.com"

            token = await client.login(email, f"Sim{agent_id}", role=role, booth_id=cfg.get("booth_id"))
            if not token:
                print(f"  [{agent_id}] 로그인 실패, 건너뜀")
                continue

            state = AgentState(
                agent_id=agent_id,
                pattern=pattern,
                persona=persona_for(pattern),
                token=token,
                assigned_booth_id=cfg.get("booth_id"),
            )
            agents.append(Agent(state, client))

        if not agents:
            print("\n⚠️  실행 가능한 에이전트가 없습니다. 로그인을 확인하세요.")
            return

        print(f"\n[Run] {len(agents)}명 동시 실행 시작\n")
        sim_start = time.monotonic()

        await asyncio.gather(*[a.run() for a in agents])

        print(f"\n[Done] 완료 ({time.monotonic() - sim_start:.1f}초)\n")

        results = collect_results(agents)
        save_results(results, phase)
        print_summary(results)


# ── 메인 (Phase 4) ───────────────────────────────────────────

async def main_phase_4(waves: int, band_arg: str):
    print(f"\n{'='*60}")
    print(f"  Festin LLM 시뮬레이션 — Phase 4 (압축 운영)")
    print(f"  파동 {waves}회, 시간대 {band_arg}")
    print(f"  시작: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"{'='*60}")

    async with aiohttp.ClientSession(timeout=aiohttp.ClientTimeout(total=15)) as session:
        client = FestinClient(session)

        for w in range(1, waves + 1):
            band = band_arg if band_arg != "rotate" else BANDS_CYCLE[(w - 1) % len(BANDS_CYCLE)]
            result = await run_wave(client, band, wave_id=w)
            if result is None:
                continue
            save_wave_result(result)
            update_cumulative_summary(result)
            print_summary(result, header=f"파동 #{w:04d} 결과 — {band}")

        # 전체 누적 요약
        summary_path = _results_dir() / "phase4" / "summary.json"
        if summary_path.exists():
            with open(summary_path, encoding="utf-8") as f:
                summary = json.load(f)
            print("\n" + "=" * 60)
            print("  Phase 4 누적 요약")
            print("=" * 60)
            print(f"  총 파동:            {summary['total_waves']}")
            print(f"  총 에이전트 누적:   {summary['total_agents']}")
            print(f"  대기 등록 누적:     {summary['total_enqueue']}")
            print(f"  취소 누적:          {summary['total_cancel']}")
            print(f"  버그 누적:          {summary['total_bugs']}")
            print(f"  부스 불균형 평균:   stdev_avg={summary['imbalance']['mean_stdev_avg']} / max_ever={summary['imbalance']['max_stdev_ever']}")
            print(f"  시간대별:           {summary['by_band']}")
            print("=" * 60)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--phase", type=int, default=1, choices=[1, 2, 3, 4])
    parser.add_argument("--waves", type=int, default=10, help="Phase 4: 파동 개수")
    parser.add_argument(
        "--band",
        choices=["lunch", "afternoon", "evening", "rotate"],
        default="rotate",
        help="Phase 4: 시간대 (rotate는 매 파동 순환)",
    )
    args = parser.parse_args()
    if args.phase == 4:
        asyncio.run(main_phase_4(args.waves, args.band))
    else:
        asyncio.run(main_phase_123(args.phase))