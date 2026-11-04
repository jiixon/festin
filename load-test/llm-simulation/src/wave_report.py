"""
Wave 결과 요약 + 이상치 감지

GitHub Actions 에서 호출되면 Step Summary 에 마크다운 표를 추가하고,
이상치 조건에 걸리면 ::warning::/::error:: 어노테이션을 출력한다.

이상치 임계값:
- 5xx > 0       → error (워크플로우 fail → GitHub 알림)
- bugs > 0      → error
- 이탈률 > 50%   → warning (서버 지연 의심)
- 전환율 < 30%   → warning (처리 누락 의심)
- LLM 환각 > 20% → warning (프롬프트/모델 점검)

로컬 실행 시에는 stdout 으로 같은 마크다운 출력.
"""

from __future__ import annotations

import json
import os
import sys
from pathlib import Path


THRESHOLDS = {
    "abandonment_rate_high": 0.5,
    "conversion_rate_low": 0.3,
    "booth_correction_rate_high": 0.2,
}


def find_wave_files() -> list[Path]:
    base = Path(__file__).parent / "results" / "phase4"
    return sorted(base.glob("wave_*.json"))


def analyze_wave(wave: dict) -> dict:
    m = wave["metrics"]
    logs = wave.get("logs", [])

    booth_corrections = sum(1 for log in logs if log.get("event") == "booth_id_corrected")
    total_decisions = len(m.get("llm_decisions", []))
    correction_rate = booth_corrections / total_decisions if total_decisions > 0 else 0

    # 5xx endpoint 별 집계 (이상 시 디버깅 단서)
    by_endpoint = {}
    for err in m.get("server_errors_5xx", []):
        ep = err.get("event", "?")
        by_endpoint[ep] = by_endpoint.get(ep, 0) + 1

    return {
        "wave_id": wave.get("wave_id"),
        "band": wave.get("band"),
        "elapsed_sec": wave.get("elapsed_sec", 0),
        "total_agents": m["total_agents"],
        "total_enqueue": m["total_enqueue"],
        "total_cancel": m["total_cancel"],
        "abandonment_rate": m.get("abandonment_rate", 0),
        "conversion_rate": m.get("conversion_rate", 0),
        "errors_5xx": len(m.get("server_errors_5xx", [])),
        "errors_5xx_by_endpoint": by_endpoint,
        "bugs": len(m.get("bugs_detected", [])),
        "booth_corrections": booth_corrections,
        "correction_rate": correction_rate,
        "imbalance_stdev": wave.get("booth_imbalance", {}).get("mean_stdev", 0),
    }


def render_markdown(analyses: list[dict]) -> str:
    lines = ["## 📊 Wave 결과 요약", ""]
    lines.append("| # | Band | 소요 | 에이전트 | 등록 | 취소 | 이탈률 | 전환율 | 5xx | LLM 환각 | 불균형 |")
    lines.append("|---|------|------|---------|------|------|--------|--------|-----|----------|--------|")
    for a in analyses:
        lines.append(
            f"| #{a['wave_id']:04d} | {a['band']} | {a['elapsed_sec']:.0f}s | "
            f"{a['total_agents']} | {a['total_enqueue']} | {a['total_cancel']} | "
            f"{a['abandonment_rate']*100:.1f}% | {a['conversion_rate']*100:.1f}% | "
            f"{a['errors_5xx']} | {a['booth_corrections']}회 ({a['correction_rate']*100:.0f}%) | "
            f"stdev={a['imbalance_stdev']:.2f} |"
        )

    # 5xx 가 있으면 endpoint 별 상세
    has_5xx = any(a["errors_5xx"] > 0 for a in analyses)
    if has_5xx:
        lines.append("")
        lines.append("### ⚠️ 5xx endpoint 별 상세")
        for a in analyses:
            if a["errors_5xx"] > 0:
                lines.append(f"- Wave #{a['wave_id']}: " + ", ".join(
                    f"`{ep}`={cnt}건" for ep, cnt in a["errors_5xx_by_endpoint"].items()
                ))

    lines.append("")
    lines.append("### 정상 범위 참고 (Phase 4 lunch 실측 기준)")
    lines.append("- 이탈률 10~25%, 전환율 45~65%")
    lines.append("- LLM 환각 < 20%, 불균형 stdev 12~25")
    return "\n".join(lines) + "\n"


def emit_annotations(analyses: list[dict]) -> bool:
    """이상치 감지 + 어노테이션 출력. has_error 반환 (True = 워크플로우 fail)."""
    has_error = False
    for a in analyses:
        prefix = f"Wave #{a['wave_id']} ({a['band']})"

        if a["errors_5xx"] > 0:
            print(f"::error::{prefix}: 5xx 서버 에러 {a['errors_5xx']}건 감지 — 서버 로그 확인 필요")
            has_error = True
        if a["bugs"] > 0:
            print(f"::error::{prefix}: 멱등성/동시성 버그 {a['bugs']}건 감지")
            has_error = True

        if a["abandonment_rate"] > THRESHOLDS["abandonment_rate_high"]:
            print(
                f"::warning::{prefix}: 이탈률 비정상 "
                f"({a['abandonment_rate']*100:.1f}% > {THRESHOLDS['abandonment_rate_high']*100:.0f}%) — 서버 지연 의심"
            )
        if a["conversion_rate"] < THRESHOLDS["conversion_rate_low"]:
            print(
                f"::warning::{prefix}: 전환율 비정상 "
                f"({a['conversion_rate']*100:.1f}% < {THRESHOLDS['conversion_rate_low']*100:.0f}%) — 처리 누락 의심"
            )
        if a["correction_rate"] > THRESHOLDS["booth_correction_rate_high"]:
            print(
                f"::warning::{prefix}: LLM 환각 비율 "
                f"{a['correction_rate']*100:.1f}% > {THRESHOLDS['booth_correction_rate_high']*100:.0f}% — 프롬프트/모델 점검"
            )

    return has_error


def main() -> int:
    waves = find_wave_files()
    if not waves:
        print("::warning::wave 결과 파일 없음")
        return 0

    analyses = []
    for path in waves:
        with open(path) as f:
            wave = json.load(f)
        analyses.append(analyze_wave(wave))

    md = render_markdown(analyses)

    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with open(summary_path, "a") as f:
            f.write(md)

    # 로컬 실행 시 stdout 으로도 보여줌
    print(md)

    has_error = emit_annotations(analyses)
    return 1 if has_error else 0


if __name__ == "__main__":
    sys.exit(main())