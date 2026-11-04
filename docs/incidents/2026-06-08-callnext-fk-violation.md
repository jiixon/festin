# 인시던트 — CallNext FK Violation (좀비 user_id)

**발생일**: 2026-06-08 (식별), 2026-06-01 (실제 첫 발생 추정)
**탐지 경로**: LLM 다중 에이전트 시뮬레이션 (Phase 4) 운영 중 서버 로그 grep
**심각도**: Medium (사용자 영향 X, 시뮬레이션 데이터 신뢰도 ↓)
**상태**: ✅ 해결 — 응급 처치(스크립트) + 근본 해결(애플리케이션 방어, 5단계)

---

## 🎯 TL;DR

LLM 시뮬레이션을 7파동(누적 384명) 돌린 뒤 시뮬 리포트는 `버그 탐지: 0건` 으로 깨끗했으나,
EC2 의 `festin-app` 컨테이너 로그를 직접 grep 해보니 `DataIntegrityViolationException` 이
**대량 발생**하고 있었다. 원인은 **Redis 큐와 RDS `users` 의 정합성 깨짐** — 큐엔 user_id 가 남아있는데
DB에서는 사라진 좀비 86명. 시뮬 메트릭이 거짓 0건을 보여준 게 더 큰 문제였다.

핵심 발견:
1. **장기 운영형 시뮬레이션이라서 표면화 됨**: 1회성 부하 테스트라면 관측 창에 안 들어왔을
   누적·정합성 결함이 7파동 누적 운영 중에 드러남 (단, 선행 원인은 운영자 수동 DELETE — 시뮬 고유 발견은 아님)
2. **시뮬 메트릭의 거짓 침묵**: 500 응답을 카운트하는 메트릭이 없어 `버그 0건` 으로 묻혔음
3. **인프라 사고는 코드보다 데이터 잔존에서 출발하는 경우가 많음**: 운영 자동화의 사각

---

## 📊 Impact

| 항목 | 값 |
|------|---|
| 좀비 user_id (Redis 잔존, RDS 부재) | **86명** |
| 영향받은 부스 큐 | 5개 (booth 4~8) |
| ZREM 총 횟수 | 89건 (중복 등록자 3명 포함) |
| 사용자 트래픽 영향 | 없음 (시뮬 전용 데이터) |
| 시뮬 리포트 신뢰도 영향 | 7파동 × `버그 0건` 표기 모두 거짓 |

---

## 🔍 Root Cause

### 설계 측면

```
EnqueueService.enqueue()
  └─ Redis Lua atomic enqueue (DB 변경 없음)

CallNextService.callNext()
  ├─ Redis dequeue
  ├─ Soft Lock 생성
  └─ MySQL INSERT INTO waiting (user_id FK → users.id)  ← 여기서 FK 위반
```

**전제조건**: Redis 큐에 들어간 `user_id` 는 RDS `users` 테이블에 반드시 존재해야 한다.

### 깨진 시점

```
SELECT MIN(id), MAX(id), COUNT(*) FROM users;
→ MIN=1511, MAX=3982, COUNT=355
SELECT AUTO_INCREMENT FROM information_schema.TABLES WHERE TABLE_NAME='users';
→ AUTO_INCREMENT=3983
```

**해석**: `AUTO_INCREMENT(3983) - COUNT(355) ≈ 3,628명이 DELETE 됨** (TRUNCATE 아님 — AI 살아있음).

### 원인

- 과거 어느 시점에 운영자가 시뮬 정리 목적으로 `DELETE FROM users` 를 수동 실행
- 같은 시점에 Redis 큐는 비우지 않음
- 결과: 큐에 좀비 user_id 가 누적
- 그 다음 파동에서 staff 가 `/waitings/call` 호출 → 좀비 user_id 로 INSERT 시도 → FK 위반

### 자동 삭제 로직 조사 (모두 부재 확인)

- ❌ 시뮬레이터 코드 (`load-test/llm-simulation/src/`)
- ❌ Spring 앱 (`@Scheduled` 2개 모두 user 미접촉)
- ❌ GitHub Actions cron
- ❌ 레포 내 shell script

→ **자동 회귀 위험 없음**. 수동 DELETE 의 단발성 사고.

---

## 🚨 Timeline

| 시점 | 사건 |
|------|------|
| 과거 (~2026-06-01 이전) | 운영자 수동 `DELETE FROM users` 실행 |
| 2026-06-01 15:14 UTC | Phase 4 파동 중 첫 FK 위반 발생 (5건 동시) |
| 2026-06-01 ~ 06-08 | 7파동 진행, 시뮬 리포트는 `버그 0건` 으로 정상 보고 |
| 2026-06-08 | 서버 로그 grep 으로 `DataIntegrityViolationException` 발견 |
| 2026-06-08 | Redis ↔ RDS 차집합으로 좀비 86명 식별 |
| 2026-06-08 | ZREM 응급 처치 + cleanup 스크립트 작성 |
| 2026-06-08 | 검증 파동 (15명) 0건 성공 |
| 2026-06-09 | 시뮬 메트릭에 5xx 카운터 추가 |

---

## 🩹 Resolution

### 1단계: 응급 처치 (좀비 ZREM)

```bash
# Redis 모든 부스 큐 멤버 수집
for b in 4 5 6 7 8; do
  docker exec festin-redis redis-cli ZRANGE queue:booth:$b 0 -1
done | sort -u > /tmp/redis_ids.txt

# RDS 실재 user_id 수집
mysql -h <rds> -u admin -p -N -e "SELECT id FROM users" festin | sort -u > /tmp/real_ids.txt

# 차집합 = 좀비
comm -23 /tmp/redis_ids.txt /tmp/real_ids.txt > /tmp/zombies.txt
# → 86명

# ZREM
while read uid; do
  for b in 4 5 6 7 8; do
    docker exec festin-redis redis-cli ZREM queue:booth:$b "$uid"
  done
done < /tmp/zombies.txt
# → 89건 성공 (중복 등록자 3명 포함)
```

**결과**:
- booth:4 61→20, booth:5 50→33, booth:6 34→33, booth:7 42→32, booth:8 104→84
- 처치 직후 `docker logs -f | grep FK` 침묵 확인

### 2단계: 검증 파동

- 파라미터: 15명 / 300초 스프레드 (t3.micro 안전 범위)
- 결과: 47분 소요, FK 위반 0건, connection reset 0건
- 사후 grep: `docker logs --since 2h | grep FK` → 0줄

### 3단계: 재발 방지 자동화

`load-test/llm-simulation/scripts/cleanup-zombie-queues.sh` 작성:

- Redis `queue:booth:*` 전체를 `--scan` 으로 동적 발견
- RDS `users.id` 와 차집합 → 좀비만 ZREM
- env 변수: `RDS_HOST`, `RDS_PASSWORD`
- `set -euo pipefail` + `mktemp -d` + `trap` 으로 안전 보장

### 4단계: 시뮬 메트릭 보강 (5xx 카운터)

`runner.py` 의 `collect_results` / `update_cumulative_summary` / `print_summary` 에
`server_errors_5xx` 추가:

- 단일 응답(`status`) + 복수 응답(`statuses`) 양쪽에서 `>= 500` 검출
- 파동 요약과 누적 summary 양쪽에 노출
- endpoint별 집계 출력

```
파동 #0001 결과 — lunch
  버그 탐지:          0건
  5xx 서버 에러:      0건        ← 신규
  ...
```

### 5단계: 애플리케이션 레벨 근본 해결

1~3단계는 **이미 생긴 좀비를 치울 뿐**이다. 사용자가 사라지는 경로(수동 DELETE, 향후 탈퇴 기능, 만료 전 JWT로의 재등록)가 남아 있는 한
좀비는 다시 생기고, 그때마다 복구 배치가 좀비를 원래 순번으로 되살려 부스를 멈춘다. 그래서 서버가 스스로 방어하도록 고쳤다.

1. **호출 시점 차단** — `CallNextService.dequeueCallableUser()`
   Soft Lock 을 **만들기 전에** `UserRepositoryPort.existsById()` 로 사용자 존재를 확인한다.
   좀비는 Soft Lock 없이 폐기하고 다음 대기자로 진행 → 배치가 되살릴 대상이 없고, 큐가 빌 때까지만 반복하므로 종료 보장.
2. **배치 2차 방어** — `SoftLockRecoveryBatch`
   호출 도중 사용자가 사라져 Soft Lock 이 남은 경우에도, 좀비면 롤백하지 않고 Soft Lock 만 제거해 루프를 끊는다.

검증을 등록 API(`EnqueueService`)에 두지 않은 이유: 등록은 최다 호출 API이고 p99 개선을 위해 DB 접근을 걷어낸 경로다.
호출은 스태프 전용 저빈도 API라 같은 검증을 사용자 응답 경로 영향 없이 둘 수 있다.

검증: `ZombieQueueCallNextTest` (Testcontainers, 5종) — 대조군으로 실유저 Soft Lock 은 여전히 원래 순번으로 복구되는지 확인.

---

## 🛡️ Prevention

| 보호막 | 역할 |
|--------|------|
| `CallNextService.dequeueCallableUser()` | 좀비를 Soft Lock 없이 폐기 — 부스 정지 원천 차단 |
| `SoftLockRecoveryBatch` 좀비 폐기 | 남은 좀비 Soft Lock 을 되살리지 않음 (2차 방어) |
| `cleanup-zombie-queues.sh` | 시뮬 시작 전 정합성 자동 복원 |
| 5xx 카운터 (`runner.py`) | 메트릭이 거짓 0건을 다시는 보여주지 않게 |
| 인시던트 문서 | 동일 패턴 재발 시 빠른 진단 |

운영 워크플로우:
```bash
export RDS_HOST=... RDS_PASSWORD=...
./scripts/cleanup-zombie-queues.sh           # 정합성 복원
python src/runner.py --phase 4 --waves 1 ... # 시뮬 실행
```

---

## 💡 Lessons

### 1. 메트릭 침묵은 가장 위험한 거짓말

`버그 탐지: 0건` 이 7파동 동안 떠 있었지만, 실제로는 500 응답이 누적되고 있었다.
**측정하지 않는 것은 존재하지 않는 것과 같다**. 5xx 같은 기본 시그널은
LLM 의도/판단 기반 메트릭(`bug_detected`) 이전에 있어야 했다.

### 2. 장기 운영형 시뮬레이션이 짧은 부하 테스트와 발견하는 결함의 종류가 다르다

이번 FK 위반은 LLM 시뮬레이션 고유의 발견이 아니다 — **운영자의 수동 DELETE**가
선행 원인이고, k6 든 다른 도구든 동일 운영 조건이면 같은 사고가 났을 것이다.
시뮬 자체가 잡은 게 아니라 **누적 운영 중에 표면화** 된 것.

그래도 의미가 있다면: 짧은 1회성 부하 테스트라면 이런 종류의 **데이터 누적·정합성 결함**은
관측 창에 안 들어왔을 것이고, 장기 운영(7파동 누적)이라는 운영 형식이 사고를 드러나게 했다.
부하 도구의 종류보다 **"운영 형식" 자체가 결함 종류를 결정**한다는 인식.

### 3. 인프라 사고의 원인은 코드보다 데이터 운영에 있다

자동 삭제 코드는 어디에도 없었다. 사고의 원인은 **운영자의 일회성 SQL** 이었다.
이런 종류의 사고를 막는 답은 더 똑똑한 코드가 아니라 **운영 자동화 + 정합성 자가복원**.

---

## 📎 관련 코드 / 커밋

- `5e6fe92` — `fix(load-test): Redis 좀비 큐 청소 스크립트 — callNext FK 위반 방지`
- `1884d93` — `feat(sim): 5xx 서버 에러 카운트/누적 집계 + endpoint별 상세`
- `84e62dc` — `fix(ci): cron timeout 25→40m, 파동 부하 30→15 (Phase 4b 안정화)`

관련 코드:
- `CallNextService.dequeueCallableUser()` — 호출 시점 좀비 차단 (근본 해결)
- `SoftLockRecoveryBatch` — 좀비 Soft Lock 폐기 (2차 방어)
- `ZombieQueueCallNextTest` — 좀비 재현 테스트 5종
- `EnqueueService.java` — Redis 만 변경 (DB 무변경)
- `load-test/llm-simulation/scripts/cleanup-zombie-queues.sh` — 응급 처치 / 시뮬 전 정리
