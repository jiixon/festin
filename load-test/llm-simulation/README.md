# LLM 기반 유저 에이전트 시뮬레이션

## 왜 이걸 만드는가

Festin은 실유저를 모을 수 없는 서비스다.
유저가 없으면 서비스의 문제를 발견할 수 없고, 발견 못 하면 개선도 없다.

그래서 **LLM으로 유저를 만들었다.**

목적은 성능 테스트가 아니다. **비즈니스 문제 발견**이다:
- 어떤 상황에서 유저가 이탈하는가
- 설계하지 않은 유저 행동이 어떤 문제를 만드는가
- 발견한 문제를 개선하면 지표가 어떻게 변하는가

---

## k6와 무엇이 다른가

k6는 스크립트대로만 움직인다. 그건 내가 이미 아는 것만 확인하는 것.

이 시뮬레이션의 핵심은 **에이전트가 현재 상황을 보고 LLM이 직접 판단**한다는 것이다.

```
k6:
  등록 → 10초 대기 → 순번 확인 → 10초 대기 → 취소
  (내가 짠 스크립트 그대로)

LLM 에이전트:
  등록 → 순번 확인 →
  "순번 8, 5분 경과, 줄이 안 줄어든다, 다른 부스는 2명"
  → LLM 판단: "다른 부스로 옮기겠다"
  → 예상하지 못한 행동 시퀀스 발생
  → 거기서 비즈니스 문제 발견
```

내가 설계하지 않은 시퀀스가 나올 때 진짜 발견이 나온다.

---

## LLM 사용 전략

**모든 행동마다 LLM을 부르지 않는다.**
단순 조회(GET)는 코드가 실행하고, **분기가 필요한 판단 포인트에서만** LLM을 호출한다.
또한, 순번이 변하지 않으면 대기 중에도 LLM을 호출하지 않는다.

```
단순 실행 (LLM 없음)               판단 포인트 (LLM 호출)
────────────────────────────────   ──────────────────────────────
GET /booths (부스 조회)             "등록할까 말까?"
GET /waitings/booth/{id}           "계속 기다릴까 / 취소할까 / 다른 부스 볼까?"
POST /waitings (등록 실행)          "취소 후 재시도할까?"
DELETE /waitings (취소 실행)
POST /waitings/call (호출)
순번 미변경 시 대기 유지 (코드 처리)
```

**LLM**: Groq — `llama-3.1-8b-instant` (무료 500k TPD)
**동시 호출 제한**: `asyncio.Semaphore(5)` — Festin API는 제한 없이 동시 실행

---

## 실행 방법

```bash
cd load-test/llm-simulation/src
pip install -r requirements.txt

export FESTIN_BASE_URL=https://api.festin.shop
export GROQ_API_KEY=your_key

python runner.py --phase 1   # 에이전트 1명 (검증)
python runner.py --phase 2   # 14명 (방문객 9 + staff 5)
python runner.py --phase 3   # 100명 + staff 5
```

---

## 목표

- **DAU 1000명** 기준 시뮬레이션
- **1달** 운영 (GitHub Actions 시간대별 자동 실행)
- 단계별 확장: 1명 → 14명 → 100명 → 1000명
- 발견 → 개선 → 재측정 사이클로 서비스 성장 수치화

---

## 현재 진행 상태

| 단계 | 상태 | 주요 발견 |
|------|------|---------|
| Phase 1 (1명) | ✅ 완료 | 에이전트 기본 동작 검증 |
| Phase 2 (14명) | ✅ 진행 중 | net_retry 중복 등록 버그 발견 [201, 201] |
| Phase 3 (100명) | 대기 중 | |
| Phase 4 (DAU 1000) | 대기 중 | GitHub Actions 스케줄 운영 |

---

## 디렉토리

```
load-test/llm-simulation/
├── README.md               ← 이 파일
├── user-patterns.md        ← 유저 행동 패턴 + 페르소나 정의
├── scenario-phases.md      ← 단계별 시나리오 및 에이전트 구성
├── architecture.md         ← 시스템 설계 및 LLM 호출 전략
├── api-reference.md        ← Festin API 레퍼런스
├── findings-template.md    ← 발견 기록 (누적)
└── src/
    ├── runner.py            ← 메인 실행. 단계별 에이전트 구성
    ├── agent.py             ← 에이전트 행동 흐름
    ├── gemini_client.py     ← Groq LLM 판단 요청
    ├── festin_client.py     ← Festin API 호출
    ├── config.py            ← 설정 (URL, BOOTH_IDS, PATTERN_DISTRIBUTION)
    └── results/             ← 실행 결과 JSON
```