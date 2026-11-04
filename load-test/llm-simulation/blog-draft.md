# 유저가 없을 때 유저를 만드는 법 — LLM 에이전트 시뮬레이션

## 시작: 실유저가 없는 서비스

Festin은 축제 당일에만 쓰이는 부스 대기열 서비스다.
평소에 실유저가 없다.

k6로 부하 테스트는 이미 했다. 근데 k6는 내가 짠 스크립트대로만 움직인다. 그건 내가 이미 아는 것만 확인하는 거다.

진짜 문제는 **내가 설계하지 않은 상황**에서 나온다.

사람은 줄이 안 줄면 다른 데로 이동한다. 앱이 느리면 버튼을 두 번 누른다. 배터리가 없으면 그냥 나가버린다.

그래서 LLM으로 유저를 만들기로 했다.

---

## 설계: LLM이 판단하고, 코드가 실행한다

가장 중요한 결정은 하나였다.

**매 API 호출마다 LLM을 부르지 않는다.**

```
단순 실행 (코드)              판단 포인트 (LLM)
──────────────────────────   ──────────────────────────────
GET /booths                  "등록할까 말까?"
GET /waitings/booth/{id}     "기다릴까 / 취소할까 / 다른 부스 볼까?"
POST /waitings               "취소 후 재시도할까?"
DELETE /waitings
```

분기가 필요한 순간에만 LLM에게 넘긴다. LLM은 페르소나를 가진 사람처럼 판단하고, 코드는 그 판단을 실행한다.

k6는 `if (queue > 5) cancel()` 같은 조건을 내가 미리 짜야 한다.
LLM은 "줄이 8명인데 5분째 안 줄어드네. 게임부스가 2명이니까 옮기겠다"고 스스로 판단한다.

에이전트는 9가지 패턴으로 나눴다.

| 패턴 | 설명 | 비율 |
|------|------|------|
| waiter | 느긋하게 기다리는 유저 | 40% |
| explorer | 여러 부스 비교 후 결정 | 20% |
| leaver | 줄 보고 포기 | 10% |
| retrier | 취소 후 다른 부스 이동 | 10% |
| ghost_browse | 구경만 하고 사라짐 | 7% |
| ghost | 등록 후 취소 없이 사라짐 | 5% |
| double_tap | 버튼 여러 번 누름 (버그 탐지) | 3% |
| net_retry | 네트워크 재시도 (버그 탐지) | 3% |
| concurrent | 동시 다중 등록 (버그 탐지) | 2% |

---

## Phase 1: 에이전트 1명 — 기반 검증

구조부터 잡았다.

```
runner.py → agent.py → groq_client.py (LLM 판단)
                     → festin_client.py (API 실행)
```

에이전트 1명이 부스 목록을 보고 → LLM이 등록 결정 → 순번 확인 → 호출됨까지 정상 동작했다.

기반은 됐다.

---

## Phase 2: 에이전트 14명 — 실제 문제들을 만나다

규모를 키우자마자 문제들이 터졌다.

### LLM API 비용 문제

처음엔 Gemini 2.5 Flash를 썼다. 10명 시뮬레이션에서 257원이 나왔다. DAU 1000 목표에선 월 수만 원이다. 게다가 503 rate limit이 계속 터졌다.

Groq(llama-3.1-8b-instant)로 전환했다. 무료 티어 500k TPD, LLM 비용 $0.

Groq도 분당 호출 제한이 있어서 `asyncio.Semaphore(5)`로 동시 LLM 호출을 5개로 제한했다.

중요한 설계 포인트: **Festin API는 제한하지 않는다.** 에이전트들이 동시에 부스 목록을 조회하고 등록하는 건 현실적인 동시성이기 때문이다. LLM 판단만 throttle하면 된다.

### LLM이 JSON을 제대로 안 줌

```
물론이죠! 여기 제 판단입니다.

```json
{"action": "enqueue", "booth_id": 5, "reason": "줄이 짧아서"}
```
또한 고려사항은...
```

마크다운 코드블록 제거와 중괄호 depth-counting으로 JSON만 추출했다.

```python
start = text.find("{")
if start != -1:
    depth = 0
    for i, ch in enumerate(text[start:], start):
        if ch == "{": depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                text = text[start:i + 1]
                break
```

LLM은 지시를 완벽하게 따르지 않는다. 방어적 파싱이 필수다.

### LLM 호출 횟수 최적화

초기 설계는 15초마다 순번 확인 → 매번 LLM 호출이었다. 40회 루프 × 에이전트 수면 호출이 폭발한다.

**핵심 최적화: 순번이 바뀔 때만 LLM 호출**

```python
last_llm_position = None

for _ in range(40):
    await asyncio.sleep(15)
    position = (await client.get_position(token, booth_id)).body.get("position")

    if position != last_llm_position:
        last_llm_position = position
        decision = await decide_during_wait(...)  # LLM 호출
    else:
        decision = {"action": "wait"}  # 코드가 직접 반환
```

Phase 2 첫 실행 189회 → 최적화 후 43회 (77% 감소).

사람도 순번이 그대로면 행동을 바꾸지 않는다.

### Redis에 CLOSED 부스가 노출됨

캐시 미스 시 DB에서 `findAllBoothInfo()`를 호출했는데, CLOSED 부스까지 다 가져와서 `booth:ids` SET에 저장했다.

`findAllOpenBoothInfo()` 메서드를 추가하고, 서버 시작 시 OPEN 부스만 Redis에 적재하는 `BoothCacheInitializer`를 만들었다.

Redis가 Primary Store인 아키텍처에서는 **초기 적재 시점**이 중요하다. 서버 재시작 시 캐시가 비어있으면 DB에서 채우는데, 그 쿼리가 잘못되면 전혀 다른 데이터가 노출된다.

### Staff 에이전트 설계 실패

처음엔 Staff 1명이 전체 부스를 담당했다. 결과적으로 booth 4에 등록한 방문객만 호출됐다. booth 5~8 방문객은 10분 내내 대기.

부스당 Staff 1명으로 재설계하고, 처리 흐름도 실제와 같이 완성했다.

```
call_next → 20초(유저 도착 대기) → confirm_entrance → 체험시간 → complete_experience
```

부스별 체험 시간도 현실을 반영했다 — 링 던지기 20초, 타코야끼 30초, 포토부스 60초, 캔들 공방 120초.

시뮬레이션의 현실감은 **시간 모델링**에서 나온다.

### 버그 발견: enqueue 중복 등록

`double_tap` 에이전트가 동일 부스에 200ms 간격으로 3번 요청을 보냈더니:

```json
{"statuses": [201, 201, 201], "bug_detected": true}
```

Redis Lua 스크립트는 이미 중복을 감지하고 `ALREADY_ENQUEUED`를 반환하고 있었다. 문제는 컨트롤러가 신규 등록이든 기존 등록이든 항상 201을 반환했다는 것이다.

409로 고치는 게 아니었다. **멱등성의 올바른 의미는 "같은 요청 → 같은 상태"**다. 이미 등록됐으면 큐는 그대로이고, 유저는 "이미 등록돼 있어요, N번이에요"를 받아야 한다.

`EnqueueResult`에 `alreadyEnqueued` 플래그를 추가하고 컨트롤러에서 201/200을 분기했다.

```java
HttpStatus status = result.alreadyEnqueued() ? HttpStatus.OK : HttpStatus.CREATED;
return ResponseEntity.status(status).body(EnqueueResponse.from(result));
```

수정 후: `[201, 200, 200]` → bug_detected = false ✅

도메인 레이어의 멱등성 설계가 HTTP 응답까지 완전히 전달되지 않았던 것이었다.

### Phase 2 최종 지표

| | 초기 | 최종 |
|---|---|---|
| 에이전트 수 | 10명 | 14명 |
| LLM 판단 횟수 | 189회 | 43회 |
| 전환율 | 55.6% | 88.9% |
| 버그 탐지 | 1건 | 0건 (수정 완료) |

---

## Phase 3: 에이전트 100명 — 지표가 거짓말을 했다

### 1차 실행: 전환율 102%

첫 실행하자마자 전환율이 102%가 나왔다.

원인은 지표 설계 오류였다.

```python
# 문제: total_enqueue / total_agents
metrics["conversion_rate"] = round(total_enqueue / len(agents), 3)
```

`double_tap`, `net_retry`가 같은 부스에 2~3번씩 요청을 보내다 보니 `total_enqueue`가 에이전트 수를 초과했다.

전환율의 올바른 의미는 "1번이라도 등록한 사람 비율"이다.

```python
# 수정: unique_enrolled / total_agents
unique_enrolled = sum(1 for a in agents if a.s.total_enqueue > 0)
metrics["conversion_rate"] = round(unique_enrolled / len(agents), 3)
```

지표 설계가 잘못되면 시스템이 멀쩡해도 숫자가 거짓말을 한다.

### 2차 실행: Redis 데이터 오염

지표 수정 후 재실행했는데 `double_tap` 에이전트가 전부 `[200, 200, 200]`을 받아서 등록 0건으로 집계됐다.

1차 실행 때 등록했던 `sim_agent_093_double_tap@test.com`이 Redis에 그대로 남아있었다. Phase 2에서 멱등성을 올바르게 고쳤는데(중복 등록 → 200), 그 200이 시뮬레이션 집계 로직과 충돌한 것이다.

재실행 전 Redis 초기화가 필요하다는 걸 여기서 배웠다.

```bash
redis-cli --scan --pattern "queue:booth:*" | xargs redis-cli del
redis-cli --scan --pattern "user:*:active_booths" | xargs redis-cli del
```

### 3차 실행: 최종 결과

| 지표 | 값 |
|------|-----|
| 전환율 | 87.0% |
| 이탈률 | 7.3% |
| 평균 이탈 순번 | 20.3번 |
| 버그 탐지 | 0건 |
| LLM 판단 횟수 | 3,345회 |

버그 0건. Phase 2에서 수정한 것들이 100명 규모에서도 버텨줬다.

### Rate Limit이 시뮬레이션 품질을 깎는다

Phase 3에서 새로 만난 문제는 Groq rate limit이었다.

Groq free tier는 분당 6000 토큰 한도다. 100명이 동시에 LLM 판단을 요청하면 순식간에 초과한다.

```
Rate limit reached: Limit 6000, Requested 462. Please try again in 4.14s.
```

재시도가 1초, 2초 대기인데 4.14초가 필요한 상황이라 3번 모두 실패하고 fallback("가장 짧은 줄 선택")으로 빠진다.

fallback이 있어서 시뮬레이션은 멈추지 않지만, 그 에이전트는 페르소나를 잃는다. "피곤한 성격이라 줄이 조금만 길어도 나간다"는 판단을 못 하고 기계적으로 최단 대기 부스로 이동한다.

이건 단순한 기술적 오류가 아니다. **시뮬레이션의 현실감이 깎이는 것**이다.

---

## Phase 1~3 지표

| | Phase 1 | Phase 2 | Phase 3 |
|---|---|---|---|
| 에이전트 수 | 1명 | 14명 | 100명 |
| 전환율 | 100% | 88.9% | 87.0% |
| 이탈률 | - | - | 7.3% |
| 평균 이탈 순번 | - | - | 20.3번 |
| 버그 탐지 | 0건 | 1건 → 수정 | 0건 |
| LLM 판단 횟수 | 1회 | 43회 | 3,345회 |

---

## 완전한 현실 재현은 불가능하다. 그래도 의미 있는 이유

LLM 시뮬레이션은 실제 유저를 완벽히 재현하지 못한다.

하지만 중요한 건 이거다.

> **내가 설계하지 않은 시퀀스를 만들어낼 수 있는가.**

이번 시뮬레이션에서 발견한 것들:
- `enqueue` 멱등성 계약 불일치 — 도메인은 멱등했는데 HTTP 응답이 늘 201이었다
- Staff 처리 흐름 누락 — 실제 서비스에서는 유저가 영원히 CALLED 상태로 남을 뻔했다
- Redis CLOSED 부스 노출 — 서버 재시작 시나리오를 생각하지 않았던 부분

---

## 덧: Phase 4에서 진짜가 나왔다

여기까지가 Phase 1~3, 즉 **설계 검증과 규모 검증**이다. 솔직히 이 시점에서는 "구조는 잘 만들었는데 결정적인 발견은 없다"는 느낌이었다.

Phase 4에서 이걸 GitHub Actions cron으로 **매일 자동 반복**하도록 바꾸고 나서 상황이 달라졌다. 매 실행을 깨끗한 상태에서 시작하지 않으니 **상태가 누적**되기 시작했고, 거기서 스태프 호출 API가 500을 뱉는 걸 발견했다. 첫 wave에서 100회 호출 중 93회 실패였다.

원인은 Redis 대기열에 남은 좀비 user_id가 MySQL FK 위반을 일으키는 것이었는데, 하필 **Soft Lock 복구 배치가 그 좀비를 원래 순번으로 정확히 되살려** 무한 루프가 되고 있었다. 정합성을 지키려고 만든 장치가 장애를 영속시키고 있었던 것이다.

급한 불은 대기열과 `users`의 차집합을 지우는 스크립트로 껐다. 하지만 그건 이미 생긴 좀비를 치우는 것뿐이라, 서버가 스스로 막도록 고쳤다. **Soft Lock을 만들기 전에 사용자 존재를 확인하고, 좀비는 Soft Lock 없이 폐기한 뒤 다음 대기자로 넘어간다.** 되살릴 대상 자체를 없앤 것이라 배치가 복원할 게 없어진다.

재미있는 건 **어디서 막을지**였다. 등록 API에서 막으면 좀비가 애초에 안 들어오니 더 깔끔해 보인다. 그런데 등록은 이 서비스에서 가장 많이 호출되는 API고, 나는 거기서 `@Transactional`을 걷어내 p99를 14초에서 558ms로 줄인 적이 있다. 거기에 DB 조회를 다시 넣는 건 내 손으로 그 개선을 무르는 일이다. 호출 API는 스태프 전용이라 부스당 수 초에 한 번 불린다. **같은 검증인데 비용이 다르다.** 정합성 검증은 "가능한 한 이른 곳"이 아니라 "가장 싼 곳"에 두는 게 맞다고 결론냈다.

수정은 Port 메서드 하나, Adapter 3줄, 서비스 2곳이 전부였다. 도메인도 스키마도 안 건드렸다. 헥사고날이 값을 한 순간은 설계할 때가 아니라 **사고가 났을 때**였다.

그리고 더 뼈아팠던 건, 그때 리포트에 **"5xx 0건"이라고 적혀 있었다**는 사실이다. 5xx를 세는 지표 자체가 없었다. 측정하지 않은 것은 관측되지 않고, 관측되지 않은 것은 없는 것처럼 보인다.

부하 테스트가 검증하는 건 "지금 이 순간의 서비스"다. 매 실행이 깨끗한 상태에서 시작하기 때문이다. 하지만 장애는 대개 시간이 지나며 쌓인 상태에서 나온다.

→ 자세한 내용은 `simulation-report-phase4.md`

측정 방법의 한계는 있다. 하지만 **개선 전후를 동일한 조건으로 비교**하면 상대적인 변화는 의미가 있다. 그리고 그 변화가 실제 코드를 고치게 만드는 근거가 된다.