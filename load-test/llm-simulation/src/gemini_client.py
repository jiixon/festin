"""
LLM 판단 포인트 모듈

에이전트가 분기 결정이 필요한 순간에만 호출된다.
단순 API 실행(GET, POST)은 여기를 거치지 않는다.
"""

import asyncio
import json
import random
import re
from groq import AsyncGroq
from config import GROQ_API_KEY


# Groq 429 메시지에 박혀 오는 "try again in 4.14s" 패턴
_RETRY_AFTER_RE = re.compile(r"try again in (\d+(?:\.\d+)?)\s*s", re.IGNORECASE)


def _extract_retry_after(err: Exception) -> float | None:
    """RateLimitError에서 정확 대기 시간(초)을 뽑는다. 헤더 우선, 메시지 fallback."""
    # 1) SDK가 노출하는 response.headers — Groq SDK 1.x
    resp = getattr(err, "response", None)
    headers = getattr(resp, "headers", None) if resp is not None else None
    if headers:
        ra = headers.get("retry-after") or headers.get("Retry-After")
        try:
            return float(ra) if ra else None
        except (TypeError, ValueError):
            pass
    # 2) 메시지에 박힌 "try again in N.NNs"
    m = _RETRY_AFTER_RE.search(str(err))
    if m:
        try:
            return float(m.group(1))
        except ValueError:
            pass
    return None

_client = AsyncGroq(api_key=GROQ_API_KEY)
_semaphore: asyncio.Semaphore | None = None


def _get_semaphore() -> asyncio.Semaphore:
    global _semaphore
    if _semaphore is None:
        _semaphore = asyncio.Semaphore(5)  # 동시 LLM 호출 최대 5개
    return _semaphore


PERSONAS = [
    "급한 성격이다. 순번이 조금만 높아도 답답해하고 더 짧은 곳을 찾아 이동한다.",
    "느긋한 성격이다. 마음에 드는 부스라면 오래 기다릴 수 있다.",
    "합리적인 성격이다. 대기 시간 대비 가치를 따져서 결정한다.",
    "충동적인 성격이다. 줄이 짧아 보이면 일단 등록하고 본다.",
    "조심스러운 성격이다. 등록 전에 상황을 충분히 살핀다.",
    "포모(FOMO) 성격이다. 인기 있어 보이는 부스에 끌린다.",
    "피곤한 성격이다. 조금만 불편해도 그냥 나가려 한다.",
]

# 패턴별 고정 페르소나 (LLM이 exit을 결정하면 edge case 동작이 발현되지 않으므로
# 해당 패턴의 기술적 행동이 의미 있으려면 등록 의사가 높은 성격이어야 한다)
FIXED_PERSONAS = {
    "double_tap": "충동적인 성격이다. 버튼 반응이 느리면 여러 번 누르는 경향이 있고, 줄이 보이면 일단 등록하고 본다.",
    "net_retry":  "급한 성격이다. 앱이 느리면 바로 재시도하고, 축제에서 빠짐없이 체험하고 싶어한다.",
    "concurrent": "충동적인 성격이다. 여러 탭을 동시에 열어두고 등록 버튼을 누르는 습관이 있다.",
}


def random_persona() -> str:
    return random.choice(PERSONAS)


def persona_for(pattern: str) -> str:
    """패턴별 고정 페르소나 반환. 없으면 랜덤."""
    return FIXED_PERSONAS.get(pattern) or random_persona()


# ── 판단 포인트 1: 부스 목록 보고 등록 여부 결정 ─────────────

async def decide_enqueue(persona: str, booths: list[dict]) -> dict:
    """
    booths: [{"name": "포토부스", "id": 3, "waiting": 12}, ...]
    returns: {"action": "enqueue"|"exit", "booth_id": int|None, "reason": str}
    """
    booth_desc = "\n".join(
        f"- {b['name']}: 현재 대기 {b.get('waiting', '?')}명 (id: {b['id']})"
        for b in booths
    )

    prompt = f"""당신은 축제에 온 방문객입니다.
성격: {persona}

현재 보이는 부스 목록:
{booth_desc}

어떻게 하겠습니까?

반드시 아래 JSON만 출력하세요. 설명 없이 JSON만.
booth_id는 반드시 위 목록의 숫자 id값이어야 합니다. 부스 이름이 아닌 숫자입니다.
{{"action": "enqueue" 또는 "exit", "booth_id": 등록할_부스의_숫자_id_또는_null, "reason": "판단_이유"}}"""

    shortest = min(booths, key=lambda b: b.get("waiting", 999)) if booths else None
    fallback = (
        {"action": "enqueue", "booth_id": shortest["id"], "reason": "LLM 판단 실패 - 가장 짧은 줄 선택"}
        if shortest else {"action": "exit", "booth_id": None, "reason": "부스 없음"}
    )
    return await _ask(prompt, fallback=fallback)


# ── 판단 포인트 2: 대기 중 행동 결정 (핵심) ──────────────────

async def decide_during_wait(
    persona: str,
    booth_name: str,
    position: int,
    elapsed_min: float,
    position_delta: int,
    other_booths: list[dict],
) -> dict:
    """
    returns: {"action": "wait"|"cancel"|"add_second_booth", "target_booth_id": int|None, "reason": str}
    """
    other_desc = "\n".join(
        f"- {b['name']}: 대기 {b.get('waiting', '?')}명 (id: {b['id']})"
        for b in other_booths
    ) if other_booths else "없음"

    trend = "줄어들고 있음" if position_delta < 0 else ("그대로" if position_delta == 0 else "늘어나고 있음")

    prompt = f"""당신은 축제 부스 대기 중인 방문객입니다.
성격: {persona}

현재 상황:
- 대기 중인 부스: {booth_name}
- 현재 순번: {position}번
- 대기 경과 시간: {elapsed_min:.1f}분
- 최근 줄 변화: {trend} (변화량: {position_delta})

다른 부스 현황:
{other_desc}

지금 어떻게 하겠습니까?

반드시 아래 JSON만 출력하세요. 설명 없이 JSON만.
{{"action": "wait" 또는 "cancel" 또는 "add_second_booth", "target_booth_id": 두번째_등록할_부스_id_또는_null, "reason": "판단_이유"}}"""

    return await _ask(prompt, fallback={"action": "wait", "target_booth_id": None, "reason": "판단 실패"})


# ── 판단 포인트 3: 취소 후 재시도 여부 ───────────────────────

async def decide_after_cancel(
    persona: str,
    cancel_reason: str,
    other_booths: list[dict],
) -> dict:
    """
    returns: {"action": "retry"|"exit", "booth_id": int|None, "reason": str}
    """
    other_desc = "\n".join(
        f"- {b['name']}: 대기 {b.get('waiting', '?')}명 (id: {b['id']})"
        for b in other_booths
    ) if other_booths else "없음"

    prompt = f"""당신은 방금 부스 대기를 취소한 축제 방문객입니다.
성격: {persona}

취소 이유: {cancel_reason}

현재 다른 부스 현황:
{other_desc}

다른 부스로 재시도하겠습니까, 아니면 그냥 나가겠습니까?

반드시 아래 JSON만 출력하세요. 설명 없이 JSON만.
{{"action": "retry" 또는 "exit", "booth_id": 재시도할_부스_id_또는_null, "reason": "판단_이유"}}"""

    shortest = min(other_booths, key=lambda b: b.get("waiting", 999)) if other_booths else None
    fallback = (
        {"action": "retry", "booth_id": shortest["id"], "reason": "LLM 판단 실패 - 가장 짧은 줄 재시도"}
        if shortest else {"action": "exit", "booth_id": None, "reason": "다른 부스 없음"}
    )
    return await _ask(prompt, fallback=fallback)


# ── 내부 헬퍼 ─────────────────────────────────────────────────

async def _ask(prompt: str, fallback: dict, retries: int = 3) -> dict:
    async with _get_semaphore():
        return await _ask_inner(prompt, fallback, retries)


async def _ask_inner(prompt: str, fallback: dict, retries: int = 3) -> dict:
    for attempt in range(retries):
        try:
            response = await _client.chat.completions.create(
                model="llama-3.1-8b-instant",
                messages=[{"role": "user", "content": prompt}],
                temperature=0.7,
            )
            text = response.choices[0].message.content.strip()

            # ```json ... ``` 블록 제거
            if "```" in text:
                text = text.split("```")[1]
                if text.startswith("json"):
                    text = text[4:]
                text = text.strip()

            # 첫 번째 완전한 JSON 객체만 추출
            start = text.find("{")
            if start != -1:
                depth = 0
                for i, ch in enumerate(text[start:], start):
                    if ch == "{":
                        depth += 1
                    elif ch == "}":
                        depth -= 1
                        if depth == 0:
                            text = text[start:i + 1]
                            break

            return json.loads(text)

        except Exception as e:
            is_rate_limit = "429" in str(e) or "rate" in str(e).lower()
            if is_rate_limit and attempt < retries - 1:
                ra = _extract_retry_after(e)
                if ra is not None:
                    wait = min(ra + 0.5, 30.0)  # 정확 대기 + 작은 여유, 폭주 방지 30s 캡
                    source = f"retry-after={ra:.2f}s"
                else:
                    wait = 2 ** attempt
                    source = "exp-backoff"
                print(f"  [Groq] rate limit 재시도 {attempt + 1}/{retries - 1} ({wait:.1f}s 후, {source})")
                await asyncio.sleep(wait)
                continue
            print(f"  [Groq] 호출 실패: {e} → fallback 사용")
            return fallback