"""
에이전트 실행 모듈

단순 API 호출은 코드가 처리한다.
분기가 필요한 판단 포인트에서만 gemini_client를 호출한다.
"""

import asyncio
import random
import time
from dataclasses import dataclass, field

from festin_client import FestinClient
from gemini_client import decide_enqueue, decide_during_wait, decide_after_cancel
from config import BOOTH_IDS, BOOTH_EXPERIENCE_SEC, BOOTH_ARRIVAL_SEC


@dataclass
class AgentState:
    agent_id: str
    pattern: str          # explorer / waiter / leaver / retrier / staff
                          # double_tap / net_retry / concurrent / ghost
    persona: str          # LLM에 전달되는 성격 설명

    token: str = ""
    assigned_booth_id: int | None = None  # staff 전용: 담당 부스 ID

    # 런타임 상태
    registered_booths: list = field(default_factory=list)
    start_time: float = field(default_factory=time.monotonic)
    last_position: int | None = None
    position_history: list = field(default_factory=list)  # [(elapsed_sec, position)]

    # 비즈니스 지표
    total_browse: int = 0
    total_enqueue: int = 0
    total_cancel: int = 0
    cancel_positions: list = field(default_factory=list)

    # 전체 행동 로그
    action_log: list = field(default_factory=list)

    @property
    def elapsed_min(self) -> float:
        return (time.monotonic() - self.start_time) / 60

    @property
    def position_delta(self) -> int:
        """최근 순번 변화량. 음수면 줄어드는 중."""
        if len(self.position_history) < 2:
            return 0
        return self.position_history[-1][1] - self.position_history[-2][1]

    def log(self, event: str, data: dict):
        entry = {
            "agent_id": self.agent_id,
            "pattern": self.pattern,
            "elapsed_sec": int((time.monotonic() - self.start_time)),
            "event": event,
            **data,
        }
        self.action_log.append(entry)

        # 콘솔 출력 (LLM 판단은 reason까지 표시)
        if "reason" in data:
            print(f"  [{self.agent_id}] {event} → {data.get('action')} | {data.get('reason')}")
        else:
            status = data.get("status", "")
            print(f"  [{self.agent_id}] {event} → {status}")


class Agent:
    def __init__(self, state: AgentState, client: FestinClient):
        self.s = state
        self.c = client

    async def run(self):
        print(f"\n[{self.s.agent_id}] 시작 — 패턴: {self.s.pattern}")
        print(f"  페르소나: {self.s.persona}")

        if self.s.pattern == "staff":
            await self._run_staff()
        elif self.s.pattern in ("double_tap", "net_retry", "concurrent"):
            await self._run_edge_case()
        else:
            await self._run_visitor()

    # ── 방문객 흐름 ───────────────────────────────────────────

    async def _run_visitor(self):
        # 1. 부스 목록 조회
        res = await self.c.get_booths(self.s.token)
        self.s.total_browse += 1
        self.s.log("browse", {"status": res.status, "latency_ms": res.latency_ms})

        if res.status != 200:
            return

        raw = res.body
        booths = raw.get("booths", []) if isinstance(raw, dict) else (raw if isinstance(raw, list) else [])
        booth_list = [
            {"id": b.get("boothId") or b.get("id"), "name": b.get("boothName") or b.get("name", "부스"), "waiting": b.get("currentWaiting", 0)}
            for b in booths
        ]

        await asyncio.sleep(2)  # 목록 훑어보는 시간

        # 유령 유저 — 탐색 후 그냥 사라짐
        if self.s.pattern == "ghost_browse":
            self.s.log("ghost_exit", {"note": "탐색만 하고 사라짐"})
            return

        # ── 판단 포인트 1: 등록 여부 ──────────────────────────
        decision = await decide_enqueue(self.s.persona, booth_list)
        self.s.log("llm_decision_enqueue", {
            "action": decision.get("action"),
            "booth_id": decision.get("booth_id"),
            "reason": decision.get("reason"),
        })

        if decision.get("action") == "exit":
            return

        booth_id = decision.get("booth_id")
        if not booth_id:
            return

        # LLM이 숫자 ID 대신 이름을 반환한 경우 매핑
        if isinstance(booth_id, str) and not str(booth_id).isdigit():
            matched = next((b for b in booth_list if b["name"] == booth_id), None)
            if matched:
                booth_id = matched["id"]
            else:
                self.s.log("enqueue_skip", {"reason": f"LLM이 유효하지 않은 booth_id 반환: {booth_id}"})
                return

        booth_id = int(booth_id)

        # LLM이 목록에 없는 id 반환 시 가장 짧은 줄로 대체
        valid_ids = [b["id"] for b in booth_list]
        if booth_id not in valid_ids:
            if not booth_list:
                return
            booth_id = min(booth_list, key=lambda b: b.get("waiting", 999))["id"]
            self.s.log("booth_id_corrected", {"reason": f"LLM이 유효하지 않은 id 반환, 최단 대기 부스로 변경: {booth_id}"})

        booth_name = next((b["name"] for b in booth_list if b["id"] == booth_id), f"부스{booth_id}")

        # 2. 등록 실행
        enrolled = await self._enqueue(booth_id)
        if not enrolled:
            return

        # 유령 유저 — 등록 후 사라짐
        if self.s.pattern == "ghost":
            self.s.log("ghost_exit", {"note": "등록 후 취소 없이 종료"})
            return

        # 3. 대기 루프
        await self._wait_loop(booth_id, booth_name, booth_list)

    # ── 대기 루프 ─────────────────────────────────────────────

    async def _wait_loop(self, booth_id: int, booth_name: str, all_booths: list):
        check_interval = 15  # 초
        last_llm_position = None  # 마지막으로 LLM 호출한 시점의 순번

        for _ in range(40):  # 최대 40회 확인 (약 10분)
            await asyncio.sleep(check_interval)

            res = await self.c.get_position(self.s.token, booth_id)
            self.s.log("check_position", {
                "status": res.status,
                "booth_id": booth_id,
                "position": res.body.get("position"),
                "latency_ms": res.latency_ms,
            })

            if res.status == 404:
                # 호출됐거나 이미 취소됨
                self.s.log("called_or_gone", {"booth_id": booth_id})
                if booth_id in self.s.registered_booths:
                    self.s.registered_booths.remove(booth_id)
                return

            if res.status != 200:
                continue

            position = res.body.get("position")
            if position is not None:
                self.s.last_position = position
                self.s.position_history.append((self.s.elapsed_min, position))

            # ── 판단 포인트 2: 순번이 바뀔 때만 LLM 호출 ──────────
            other_booths = [b for b in all_booths if b["id"] != booth_id]
            position_changed = (position != last_llm_position)

            if position_changed:
                last_llm_position = position
                decision = await decide_during_wait(
                    persona=self.s.persona,
                    booth_name=booth_name,
                    position=position or 0,
                    elapsed_min=self.s.elapsed_min,
                    position_delta=self.s.position_delta,
                    other_booths=other_booths,
                )
            else:
                decision = {"action": "wait", "target_booth_id": None, "reason": "대기 유지"}
            self.s.log("llm_decision_wait", {
                "action": decision.get("action"),
                "position": position,
                "elapsed_min": round(self.s.elapsed_min, 1),
                "reason": decision.get("reason"),
            })

            action = decision.get("action")

            if action == "cancel":
                self.s.cancel_positions.append(position)
                await self._cancel(booth_id, position=position, reason=decision.get("reason", ""))

                # ── 판단 포인트 3: 취소 후 재시도 여부 ──────────
                retry_decision = await decide_after_cancel(
                    persona=self.s.persona,
                    cancel_reason=decision.get("reason", ""),
                    other_booths=other_booths,
                )
                self.s.log("llm_decision_after_cancel", {
                    "action": retry_decision.get("action"),
                    "booth_id": retry_decision.get("booth_id"),
                    "reason": retry_decision.get("reason"),
                })

                if retry_decision.get("action") == "retry" and retry_decision.get("booth_id"):
                    new_booth_id = retry_decision["booth_id"]
                    if isinstance(new_booth_id, str) and not str(new_booth_id).isdigit():
                        matched = next((b for b in all_booths if b["name"] == new_booth_id), None)
                        new_booth_id = matched["id"] if matched else None
                    if not new_booth_id:
                        return
                    new_booth_id = int(new_booth_id)
                    new_booth_name = next(
                        (b["name"] for b in all_booths if b["id"] == new_booth_id),
                        f"부스{new_booth_id}"
                    )
                    enrolled = await self._enqueue(new_booth_id)
                    if enrolled:
                        await self._wait_loop(new_booth_id, new_booth_name, all_booths)
                return

            elif action == "add_second_booth":
                target_id = decision.get("target_booth_id")
                if target_id and target_id not in self.s.registered_booths:
                    await self._enqueue(target_id)
                # 기존 부스 계속 대기

    # ── 등록 실행 ─────────────────────────────────────────────

    async def _enqueue(self, booth_id: int) -> bool:
        if self.s.pattern == "double_tap":
            # 연타형: 동일 요청 연속 2~3회
            statuses = []
            for _ in range(3):
                r = await self.c.enqueue(self.s.token, booth_id)
                statuses.append(r.status)
                await asyncio.sleep(0.2)
            bug = statuses.count(201) > 1
            self.s.log("double_tap_enqueue", {
                "booth_id": booth_id,
                "statuses": statuses,
                "bug_detected": bug,
            })
            if bug:
                print(f"  [{self.s.agent_id}] ⚠️  BUG: 연타에서 중복 등록 발생! {statuses}")
            if 201 in statuses:
                self.s.registered_booths.append(booth_id)
                self.s.total_enqueue += 1
                return True
            return False

        if self.s.pattern == "net_retry":
            # 네트워크 재시도형: 첫 요청 후 바로 재전송
            r1 = await self.c.enqueue(self.s.token, booth_id)
            await asyncio.sleep(0.5)
            r2 = await self.c.enqueue(self.s.token, booth_id)
            bug = r1.status == 201 and r2.status == 201
            self.s.log("net_retry_enqueue", {
                "booth_id": booth_id,
                "statuses": [r1.status, r2.status],
                "bug_detected": bug,
            })
            if bug:
                print(f"  [{self.s.agent_id}] ⚠️  BUG: 네트워크 재시도에서 중복 등록 발생!")
            if r1.status == 201 or r2.status == 201:
                self.s.registered_booths.append(booth_id)
                self.s.total_enqueue += 1
                return True
            return False

        # 일반 등록
        r = await self.c.enqueue(self.s.token, booth_id)
        self.s.log("enqueue", {
            "booth_id": booth_id,
            "status": r.status,
            "body": r.body,
            "latency_ms": r.latency_ms,
        })
        if r.status == 201:
            self.s.total_enqueue += 1
            self.s.registered_booths.append(booth_id)
            return True
        return False

    async def _cancel(self, booth_id: int, position: int | None = None, reason: str = ""):
        r = await self.c.cancel(self.s.token, booth_id)
        self.s.total_cancel += 1
        self.s.log("cancel", {
            "booth_id": booth_id,
            "status": r.status,
            "position_at_cancel": position,
            "reason": reason,
            "latency_ms": r.latency_ms,
        })
        if booth_id in self.s.registered_booths:
            self.s.registered_booths.remove(booth_id)

    # ── 동시 탭형 ─────────────────────────────────────────────

    async def _run_edge_case(self):
        if self.s.pattern != "concurrent":
            await self._run_visitor()
            return

        # 부스 결정: 파동 코디네이터가 지정해줬으면 그걸로, 아니면 목록에서 랜덤 선택.
        # (Phase 3에서 booths[0] 고정 + skip 무로깅이라 0건 원인 추적 어려웠던 부분 보강)
        booth_id = self.s.assigned_booth_id
        if booth_id is None:
            res = await self.c.get_booths(self.s.token)
            self.s.total_browse += 1
            if res.status != 200:
                self.s.log("concurrent_skip", {"reason": f"부스 조회 실패 status={res.status}"})
                return

            raw = res.body
            booths = raw.get("booths", []) if isinstance(raw, dict) else (raw if isinstance(raw, list) else [])
            valid = [b for b in booths if (b.get("boothId") or b.get("id"))]
            if not valid:
                self.s.log("concurrent_skip", {
                    "reason": "유효한 부스 없음",
                    "raw_count": len(booths),
                })
                return
            chosen = random.choice(valid)  # 에이전트마다 다른 부스 → 자연스러운 충돌 분포
            booth_id = chosen.get("boothId") or chosen.get("id")

        booth_id = int(booth_id)

        # 동일 토큰으로 동시에 2번 등록 (멱등성 검증: [201, 200] 정상 / [201, 201] 버그)
        results = await asyncio.gather(
            self.c.enqueue(self.s.token, booth_id),
            self.c.enqueue(self.s.token, booth_id),
        )
        statuses = [r.status for r in results]
        bug = statuses.count(201) > 1
        self.s.log("concurrent_enqueue", {
            "booth_id": booth_id,
            "statuses": statuses,
            "bug_detected": bug,
        })
        if bug:
            print(f"  [{self.s.agent_id}] ⚠️  BUG: 동시 탭에서 중복 등록 발생! {statuses}")

        # 멱등 처리 후 정상 등록 1건으로 카운트 (Phase 3에선 누락돼 0건으로 집계됐던 부분)
        if 201 in statuses:
            self.s.registered_booths.append(booth_id)
            self.s.total_enqueue += 1

    # ── 스태프 흐름 ───────────────────────────────────────────

    async def _run_staff(self):
        booth_id = self.s.assigned_booth_id or next(iter(BOOTH_IDS.values()))
        experience_sec = BOOTH_EXPERIENCE_SEC.get(booth_id, 30)
        arrival_sec = BOOTH_ARRIVAL_SEC

        # 빈 큐가 N회 연속이면 파동 종료로 보고 빠짐 (압축 운영 시 staff 페이싱 비효율 제거).
        # Phase 4 lunch wave 1차에서 22분 중 ~3분이 빈 큐 sleep 으로 소비된 발견에 따른 보정.
        max_empty_streak = 3
        empty_streak = 0

        for _ in range(20):
            # 1. 다음 손님 호출
            r = await self.c.call_next(self.s.token, booth_id)
            self.s.log("call_next", {
                "booth_id": booth_id,
                "status": r.status,
                "queue_empty": r.status == 404,
                "latency_ms": r.latency_ms,
            })

            if r.status != 200:
                empty_streak += 1
                if empty_streak >= max_empty_streak:
                    self.s.log("staff_exit_drained", {
                        "booth_id": booth_id,
                        "empty_streak": empty_streak,
                    })
                    return
                await asyncio.sleep(arrival_sec)
                continue

            waiting_id = r.body.get("waitingId")
            if not waiting_id:
                empty_streak += 1
                if empty_streak >= max_empty_streak:
                    self.s.log("staff_exit_drained", {
                        "booth_id": booth_id,
                        "empty_streak": empty_streak,
                    })
                    return
                await asyncio.sleep(arrival_sec)
                continue

            empty_streak = 0  # 손님 받았으니 리셋

            # 2. 유저 도착 대기
            await asyncio.sleep(arrival_sec)

            # 3. 입장 확인
            r_entrance = await self.c.confirm_entrance(self.s.token, booth_id, waiting_id)
            self.s.log("confirm_entrance", {
                "booth_id": booth_id,
                "waiting_id": waiting_id,
                "status": r_entrance.status,
                "latency_ms": r_entrance.latency_ms,
            })

            # 4. 부스 체험 시간 대기
            await asyncio.sleep(experience_sec)

            # 5. 체험 완료
            r_complete = await self.c.complete_experience(self.s.token, booth_id, waiting_id)
            self.s.log("complete_experience", {
                "booth_id": booth_id,
                "waiting_id": waiting_id,
                "status": r_complete.status,
                "latency_ms": r_complete.latency_ms,
            })