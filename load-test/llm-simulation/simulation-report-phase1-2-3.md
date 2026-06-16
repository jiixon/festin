# 유저가 없을 때 유저를 만드는 법: LLM 에이전트 시뮬레이션 Phase 1~2~3 회고

## 왜 이걸 시작했나

Festin은 실제 축제 당일에만 쓰이는 서비스다.
평소에 실유저가 없다. 유저가 없으면 서비스의 문제를 발견할 수 없다.

k6로 부하 테스트는 이미 했다. 근데 k6는 내가 짠 스크립트대로만 움직인다.
그건 내가 이미 아는 것만 확인하는 거다.

진짜 문제는 **내가 설계하지 않은 상황**에서 나온다.
사람은 순번을 보고 눈치를 본다. 줄이 안 줄면 다른 데로 이동한다.
앱이 느리면 버튼을 두 번 누른다. 배터리가 없으면 그냥 나가버린다.

그래서 **LLM으로 유저를 만들기로 했다.**

---

## 핵심 설계 원칙: LLM이 판단하고, 코드가 실행한다

가장 중요한 설계 결정은 이거다.

**매 API 호출마다 LLM을 부르지 않는다.**

```
단순 실행 (코드)              판단 포인트 (LLM)
──────────────────────────   ──────────────────────────────
GET /booths                  "등록할까 말까?"
GET /waitings/booth/{id}     "기다릴까 / 취소할까 / 다른 부스 볼까?"
POST /waitings               "취소 후 재시도할까?"
DELETE /waitings
POST /waitings/call
```

분기가 필요한 순간에만 LLM에게 상황을 넘긴다.
LLM은 페르소나를 가진 사람처럼 판단하고, 코드는 그 판단을 실행한다.

이것이 k6와의 차이다.
k6는 `if (queue > 5) cancel()` 같은 조건을 내가 미리 짜야 한다.
LLM은 "줄이 8명인데 5분째 안 줄어드네. 게임부스가 2명이니까 옮기겠다"고 스스로 판단한다.

---

## Phase 1: 에이전트 1명 — 기반 검증

### 목표
LLM이 실제로 상황을 보고 판단하는지 확인.

### 구조

```
runner.py → agent.py → gemini_client.py (LLM 판단)
                     → festin_client.py (API 실행)
```

### 결과
- 에이전트 1명, 전환율 100%, LLM 판단 1회
- 부스 목록 보고 등록 → 순번 확인 → 호출됨까지 정상 동작

기반은 됐다.

---

## Phase 2: 에이전트 10명 → 14명 — 실제 문제들을 만나다

여기서부터 진짜 이슈들이 쏟아졌다.

---

### 문제 1: LLM API 비용과 Rate Limit

**처음 선택: Gemini 2.5 Flash**

10명 시뮬레이션에서 257원이 나왔다.
1000명 DAU 목표에서 이 비율이면 월 수만 원.

게다가 503 rate limit이 계속 터졌다.

**해결: Groq (llama-3.1-8b-instant)로 전환**

- 무료 티어: 500k TPD
- LLM 비용: $0

하지만 Groq도 분당 호출 제한이 있어서 **`asyncio.Semaphore(5)`** 로 동시 LLM 호출을 5개로 제한했다.

```python
_semaphore: asyncio.Semaphore | None = None

async def _ask(prompt: str, fallback: dict) -> dict:
    async with _get_semaphore():  # LLM만 제한
        return await _ask_inner(prompt, fallback)
```

중요한 설계 포인트: **Festin API 호출은 제한하지 않는다.**
에이전트들이 동시에 부스 목록을 조회하고 등록하는 건 현실적인 동시성이기 때문이다.
LLM 판단만 throttle하면 된다.

---

### 문제 2: LLM이 JSON을 제대로 반환하지 않음

Groq이 JSON 대신 이런 걸 반환했다:

```
물론이죠! 여기 제 판단입니다.

```json
{"action": "enqueue", "booth_id": 5, "reason": "줄이 짧아서"}
```

또한 고려사항은...
```

두 가지 처리가 필요했다:
1. 마크다운 코드블록 제거
2. **중괄호 depth-counting**으로 첫 번째 완전한 JSON 객체만 추출

```python
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
```

LLM은 항상 지시를 완벽하게 따르지 않는다. **방어적 파싱**이 필수다.

---

### 문제 3: LLM이 부스 ID 대신 이름을 반환

프롬프트에 "숫자 id"라고 명시했는데도 LLM이 `"booth_id": "포토부스"` 같이 반환했다.

두 가지 방어 처리를 추가했다:

```python
# 1. 이름 반환 시 ID로 매핑
if isinstance(booth_id, str) and not str(booth_id).isdigit():
    matched = next((b for b in booth_list if b["name"] == booth_id), None)
    if matched:
        booth_id = matched["id"]

# 2. 목록에 없는 ID 반환 시 최단 대기 부스로 대체
valid_ids = [b["id"] for b in booth_list]
if booth_id not in valid_ids:
    booth_id = min(booth_list, key=lambda b: b.get("waiting", 999))["id"]
```

LLM의 출력은 항상 검증해야 한다. **Fallback 전략**이 없으면 에이전트가 조용히 죽는다.

---

### 문제 4: LLM 호출 횟수 최적화

초기 설계: 15초마다 순번 확인 → 매번 LLM 호출
→ 40회 루프 × 에이전트 수 = LLM 호출 폭발

**핵심 최적화: 순번이 바뀔 때만 LLM 호출**

```python
last_llm_position = None

for _ in range(40):
    await asyncio.sleep(15)
    res = await self.c.get_position(self.s.token, booth_id)
    position = res.body.get("position")

    if position != last_llm_position:  # 순번이 바뀌었을 때만
        last_llm_position = position
        decision = await decide_during_wait(...)
    else:
        decision = {"action": "wait", "reason": "대기 유지"}  # 코드가 직접 반환
```

결과: Phase 2 첫 실행 189회 → 최적화 후 43회 (77% 감소)

사람도 순번이 그대로면 행동을 바꾸지 않는다. **상황이 바뀔 때만 판단**하는 것이 현실적이고 효율적이다.

---

### 문제 5: Redis에 CLOSED 부스가 노출됨

캐시 미스 시 DB에서 `findAllBoothInfo()`를 호출했는데, 이게 CLOSED 부스까지 다 가져와서 `booth:ids` SET에 저장했다.

```java
// 문제: CLOSED 부스까지 캐시에 적재
private BoothListResult getBoothListFromDbAndWarmCache() {
    List<BoothInfo> boothInfoList = boothRepositoryPort.findAllBoothInfo(); // ← 전체 조회
```

두 가지를 수정했다:

1. **`findAllOpenBoothInfo()`** 메서드 추가 — WHERE status = 'OPEN' 조건
2. **`BoothCacheInitializer`** 추가 — 서버 시작 시 OPEN 부스만 Redis에 적재

```java
@Component
public class BoothCacheInitializer implements ApplicationRunner {
    public void run(ApplicationArguments args) {
        List<BoothInfo> openBooths = boothRepositoryPort.findAllOpenBoothInfo();
        for (BoothInfo info : openBooths) {
            boothCachePort.addBoothId(info.id());
            // ...
        }
    }
}
```

**Redis가 Primary Store인 아키텍처에서는 초기 적재 시점이 중요하다.**
서버 재시작 시 캐시가 비어있으면 DB에서 채우는데, 그 쿼리가 잘못되면 전혀 다른 데이터가 노출된다.

---

### 문제 6: Staff 에이전트 설계 실패 → 재설계

**첫 설계**: Staff 1명이 전체 부스 담당
```python
booth_id = next(iter(BOOTH_IDS.values()))  # 항상 booth_id=4만
```

결과: booth 4에만 등록한 방문객만 호출됨. booth 5~8 방문객은 10분 내내 대기.

**재설계**: 부스당 staff 1명

```python
# runner.py
PHASE_2_AGENTS = [
    ...
    {"pattern": "staff", "booth_id": 4},
    {"pattern": "staff", "booth_id": 5},
    {"pattern": "staff", "booth_id": 6},
    {"pattern": "staff", "booth_id": 7},
    {"pattern": "staff", "booth_id": 8},
]
```

그리고 staff 흐름도 실제와 같이 완성했다:

```
call_next (waitingId 획득)
    ↓ 20초 (유저 도착 대기)
confirm_entrance
    ↓ 부스별 체험 시간 (20~120초)
complete_experience
    ↓ 다음 손님
```

부스별 처리 시간도 현실을 반영했다:
- 링 던지기: 20초 (빠른 게임)
- 타코야끼: 30초 (빠른 음식)
- 즉석 포토부스: 60초 (촬영+인화)
- 캔들 공방: 120초 (만들기 체험)

**시뮬레이션의 현실감은 시간 모델링에서 나온다.**
처리 시간이 균일하면 모든 부스가 똑같이 보인다.

---

### 문제 7: Edge Case 에이전트가 LLM 눈치를 봄

`double_tap`, `net_retry` 패턴은 버그를 탐지하기 위한 패턴이다.
그런데 `_run_visitor()`를 타면서 LLM이 `exit`을 결정하면 버그 탐지 코드에 아예 도달 못 한다.

```
double_tap 에이전트
    → _run_visitor()
    → LLM: "줄이 길어서 그냥 나가겠다"
    → 종료 (버그 탐지 코드 미도달)
```

**해결: 페르소나 고정**

```python
FIXED_PERSONAS = {
    "double_tap": "충동적인 성격이다. 버튼 반응이 느리면 여러 번 누르는 경향이 있고, 줄이 보이면 일단 등록하고 본다.",
    "net_retry":  "급한 성격이다. 앱이 느리면 바로 재시도하고, 축제에서 빠짐없이 체험하고 싶어한다.",
}
```

중요한 설계 원칙: **엣지케이스 패턴은 "어떻게 등록하는가"의 문제이지 "등록할지 말지"의 문제가 아니다.**
연타는 등록하려는 사람이 하는 행동이다. 페르소나도 그에 맞게 고정해야 한다.

---

### 버그 발견: enqueue 중복 등록 [201, 201, 201]

`double_tap` 에이전트가 동일 부스에 200ms 간격으로 3번 요청을 보냈더니:

```json
{"statuses": [201, 201, 201], "bug_detected": true}
```

**Lua 스크립트는 이미 중복을 감지하고 있었다.** `ALREADY_ENQUEUED(2)`를 반환했는데, `EnqueueResultFactory`가 이를 정상 결과로 변환하고 컨트롤러는 항상 201을 반환했다.

```java
// 문제: 신규 등록이든 기존 등록이든 항상 201
return ResponseEntity.status(HttpStatus.CREATED).body(EnqueueResponse.from(result));
```

**핵심 질문: 이걸 409로 고쳐야 하나?**

아니다. 멱등성의 올바른 의미는 "같은 요청 → 같은 상태"다.
이미 등록됐으면 큐는 그대로이고, 유저는 "이미 등록돼 있어요, N번이에요"를 받아야 한다.

**해결: 201 / 200 분기**

```java
// EnqueueResult에 플래그 추가
public record EnqueueResult(
    ...
    boolean alreadyEnqueued  // 신규: false, 기존: true
) {}

// 컨트롤러에서 분기
HttpStatus status = result.alreadyEnqueued() ? HttpStatus.OK : HttpStatus.CREATED;
return ResponseEntity.status(status).body(EnqueueResponse.from(result));
```

수정 후 시뮬레이션:
```
[201, 200, 200] → bug_detected = false ✅
```

도메인 레이어의 멱등성 설계가 HTTP 응답까지 완전히 전달되지 않았던 것이었다.

---

## Phase 1~2 지표 비교

| | Phase 1 | Phase 2 (초기) | Phase 2 (최종) |
|---|---|---|---|
| 에이전트 수 | 1명 | 10명 | 14명 |
| LLM 판단 횟수 | 1회 | 189회 | 43회 |
| 전환율 | 100% | 55.6% | 88.9% |
| 버그 탐지 | 0건 | 1건 | 0건 (수정 완료) |
| Staff 처리 | - | 1명, booth 4만 | 5명, booth 4~8 각 담당 |

---

## 아키텍처 설계 원칙 — 정리

### 1. LLM은 분기 포인트에만

```
❌ 모든 API 호출 후 LLM
✅ 인간의 판단이 필요한 순간에만 LLM
```

비용도 줄고, 오히려 더 현실적이다. 사람도 모든 행동을 생각하면서 하지 않는다.

### 2. 상황이 바뀔 때만 판단

```
❌ N초마다 LLM 호출
✅ 순번이 변했을 때만 LLM 호출
```

사람도 상황이 그대로면 행동을 바꾸지 않는다.

### 3. 엣지케이스와 정상 흐름을 분리하되, 강제하지 않는다

```
❌ edge case 패턴 → 강제 등록
✅ edge case 패턴 → 등록 의향이 높은 페르소나 고정 → LLM이 자연스럽게 등록 결정
```

버그 탐지의 목적은 "등록 시 어떤 일이 일어나는가"이지 "등록 강제"가 아니다.

### 4. Staff는 현실적인 처리 사이클이 있어야 한다

```
❌ call_next 만 반복
✅ call_next → 도착 대기 → confirm_entrance → 체험 시간 → complete_experience
```

Staff가 실제 처리 흐름을 따르지 않으면 방문객의 상태 전환이 일어나지 않는다.

### 5. LLM 출력은 항상 방어적으로 파싱한다

- JSON이 아닌 텍스트가 섞일 수 있다
- 필드 타입이 다를 수 있다 (문자열 ID, 이름으로 응답 등)
- Fallback이 없으면 에이전트가 조용히 멈춘다

### 6. Festin API와 LLM API는 다른 concurrency 정책을 갖는다

```python
# Festin API: 제한 없음 (실제 동시 접속 시뮬레이션)
await asyncio.gather(*[a.run() for a in agents])

# LLM API: Semaphore로 제한 (rate limit 방지)
async with asyncio.Semaphore(5):
    result = await llm_client.ask(prompt)
```

---

## 완전한 현실 재현은 불가능하다. 그래도 의미 있는 이유

LLM 시뮬레이션은 실제 유저를 완벽히 재현하지 못한다.

하지만 중요한 건 이거다:
> **내가 설계하지 않은 시퀀스를 만들어낼 수 있는가.**

이번 Phase 2에서 발견한 것들:
- `enqueue` 중복 등록 버그 — k6 스크립트로는 절대 발견 못 했을 것
- Staff 처리 흐름 누락 — 실제 서비스에서는 유저가 영원히 CALLED 상태로 남을 뻔했다
- Redis CLOSED 부스 노출 — 서버 재시작 시나리오를 생각하지 않았던 부분

측정 방법의 한계는 있다. 하지만 **개선 전후를 동일한 조건으로 비교**하면 상대적인 변화는 의미가 있다.
그리고 그 변화가 실제 코드를 고치게 만드는 근거가 된다.

---

## 다음: Phase 3 (100명)

Phase 2가 **회귀 테스트**라면, Phase 3는 **피크 시뮬레이션**이다.

100명이 동시에 접속했을 때:
- Redis 대기열 처리 성능
- HikariCP Connection Pool 압박
- 순번 중복 발생 여부
- 부스별 대기열 불균형

Phase 2에서 고친 것들이 Phase 3에서 어떻게 버텨주는지가 다음 이야기다.

---

# Phase 3: 에이전트 100명 — 지표가 거짓말을 했다

## 목표

Phase 2까지는 설계 검증이었다. Phase 3는 **규모 검증**이다.

100명 동시 접속 시:
- 대기열 처리가 올바른가
- 에이전트 패턴 분포가 현실적인 지표를 만드는가
- Phase 2에서 수정한 멱등성 처리가 실제로 동작하는가

---

## 1차 실행: 전환율 102%

첫 실행 결과:

| 지표 | 값 |
|------|-----|
| 전환율 | **102%** |
| 이탈률 | 13.7% |
| 버그 탐지 | 0건 |

전환율이 100%를 넘겼다. 즉시 지표 계산 로직을 의심했다.

**원인: 지표 설계 오류**

```python
# 문제: total_enqueue / total_agents
metrics["conversion_rate"] = round(total_enqueue / len(agents), 3)
```

`double_tap`, `net_retry` 패턴이 같은 부스에 2~3번씩 등록 요청을 보내다 보니 `total_enqueue`가 에이전트 수를 초과했다.

전환율의 올바른 의미는 "1번이라도 등록한 사람 비율"이다.

```python
# 수정: unique_enrolled / total_agents
unique_enrolled = sum(1 for a in agents if a.s.total_enqueue > 0)
metrics["conversion_rate"] = round(unique_enrolled / len(agents), 3)
```

지표 설계가 잘못되면 시스템이 멀쩡해도 숫자가 거짓말을 한다.

---

## 2차 실행: 크래시와 데이터 오염

지표 수정 후 재실행했는데 두 가지 문제가 동시에 터졌다.

**Redis 데이터 오염**

`double_tap` 에이전트가 `[200, 200, 200]`을 받아서 등록 0건으로 집계됐다.

1차 실행 때 등록했던 `sim_agent_093_double_tap@test.com`이 Redis에 그대로 남아있었다. 재실행했을 때 첫 요청부터 200(이미 등록)이 반환됐고, `_enqueue`는 `if 201 in statuses`만 체크하고 있어서 카운트가 안 된 것이다.

Phase 2에서 멱등성을 올바르게 고쳤는데(중복 등록 → 200), 그 200이 시뮬레이션 집계 로직과 충돌했다.

재실행 전 Redis 초기화가 필요하다는 걸 여기서 배웠다.

```bash
redis-cli --scan --pattern "queue:booth:*" | xargs redis-cli del
redis-cli --scan --pattern "user:*:active_booths" | xargs redis-cli del
```

`booth:*`는 부스 메타 데이터라 지우면 안 된다.

---

## 3차 실행: 최종 결과

Redis 초기화 후 재실행한 결과:

| 지표 | 값 |
|------|-----|
| 총 에이전트 | 100명 |
| 전환율 | **87.0%** |
| 이탈률 | **7.3%** |
| 평균 이탈 순번 | **20.3번** |
| 버그 탐지 | **0건** |
| LLM 판단 횟수 | 3,345회 |

**패턴별 집계**

| 패턴 | 인원 | 등록 | 취소 |
|------|------|------|------|
| waiter | 40명 | 43회 | 4회 |
| explorer | 20명 | 21회 | 1회 |
| leaver | 10명 | 11회 | 2회 |
| retrier | 10명 | 10회 | 0회 |
| ghost_browse | 7명 | 0회 | 0회 |
| ghost | 5명 | 5회 | 0회 |
| double_tap | 3명 | 3회 | 0회 |
| net_retry | 3명 | 3회 | 0회 |
| concurrent | 2명 | 0회 | 0회 |

`concurrent` 0건은 응답 포맷 파싱 버그로 부스 목록을 가져오지 못해서다. Phase 4에서 피크 시간 시나리오로 재설계할 예정.

---

## Groq Rate Limit: 100명이 동시에 판단하면

Phase 3에서 새로 만난 문제는 **LLM rate limit**이었다.

Groq free tier는 분당 6000 토큰(TPM) 한도가 있다. 100명이 동시에 `decide_enqueue`를 호출하면 `Semaphore(5)`로 5개씩 처리하더라도 순식간에 TPM을 초과한다.

```
Rate limit reached: Limit 6000, Requested 462. Please try again in 4.14s.
```

현재 재시도는 1초, 2초 대기 후 3번 시도인데, 4.14초가 필요한 상황에서는 3번 모두 실패하고 fallback으로 빠진다.

fallback은 "가장 짧은 줄 선택"이라 시뮬레이션이 멈추지는 않는다. 하지만 그 에이전트는 페르소나를 잃는다. "피곤한 성격이라 줄이 조금만 길어도 나간다"는 판단을 못 하고 기계적으로 최단 대기 부스로 이동한다.

이건 단순한 기술적 오류가 아니다. **시뮬레이션의 현실감이 깎이는 것**이다.

Phase 4에서는 retry 로직을 Groq가 반환하는 `retry-after` 값을 파싱해서 정확히 기다리도록 개선할 예정이다.

---

## Phase 1~3 지표 비교

| | Phase 1 | Phase 2 | Phase 3 |
|---|---|---|---|
| 에이전트 수 | 1명 | 14명 | 100명 |
| 전환율 | 100% | 88.9% | 87.0% |
| 이탈률 | - | - | 7.3% |
| 평균 이탈 순번 | - | - | 20.3번 |
| 버그 탐지 | 0건 | 1건 → 수정 | 0건 |
| LLM 판단 횟수 | 1회 | 43회 | 3,345회 |

---

## 다음: Phase 4 (DAU 1000)

Phase 3가 **피크 시뮬레이션**이었다면, Phase 4는 **운영 시뮬레이션**이다.

GitHub Actions 스케줄로 자동 실행하고, 피크 시간대(점심, 오후, 저녁)에 트래픽이 몰리는 패턴을 구현할 예정이다.

Phase 3에서 남긴 숙제:
- Groq `retry-after` 기반 재시도 로직 개선
- concurrent 패턴을 피크 시간 시나리오로 재설계
- 부스별 대기열 불균형 지표 추가