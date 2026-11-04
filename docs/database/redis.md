# Redis 데이터 구조

## 📌 개요

Redis는 **대기열 관리**, **캐싱**, **분산 락**에 사용됩니다.

**특징**:
- 호출 전 대기자는 Redis에만 존재 (MySQL 저장 안 함)
- 빠른 응답 속도 (< 100ms 목표)
- 원자적 연산 보장 (INCR, DECR, ZADD, ZPOPMIN 등)

---

## 🗂️ 데이터 구조

### 1️⃣ 대기열 (Sorted Set)

**키**: `queue:booth:{boothId}`

**용도**: 부스별 대기열 관리

**구조**:
```redis
ZADD queue:booth:1 1733900000 "123"  # userId=123, timestamp=1733900000
ZADD queue:booth:1 1733900001 "456"  # userId=456, timestamp=1733900001

ZRANGE queue:booth:1 0 -1 WITHSCORES
# 결과: ["123", "1733900000", "456", "1733900001"]
```

| 필드 | 설명 |
|------|------|
| **score** | 등록 시간 (timestamp, milliseconds) |
| **value** | 사용자 ID (userId) |

**주요 연산**:
- `ZADD`: 대기 등록
- `ZPOPMIN`: 호출 시 가장 앞 사람 제거
- `ZRANK`: 순번 조회 (0-based)
- `ZCARD`: 전체 대기자 수

**TTL**: 없음 (명시적 삭제 필요)

---

### 2️⃣ 활성 부스 목록 (Set)

**키**: `user:{userId}:active_booths`

**용도**: 사용자가 현재 대기 중인 부스 목록 (최대 2개 제한 검증)

**구조**:
```redis
SADD user:123:active_booths 1
SADD user:123:active_booths 5

SMEMBERS user:123:active_booths
# 결과: ["1", "5"]

SCARD user:123:active_booths
# 결과: 2
```

**주요 연산**:
- `SADD`: 대기 등록 시 부스 추가
- `SREM`: 호출/취소 시 부스 제거
- `SCARD`: 활성 부스 개수 확인 (최대 2개 제한)

**TTL**: 없음 (명시적 삭제 필요)

---

### 3️⃣ 부스 현재 인원 (String)

**키**: `booth:{boothId}:current`

**용도**: 부스 정원 관리 (입장/완료 시 증감)

**구조**:
```redis
SET booth:1:current 40

INCR booth:1:current  # 입장 확인 시
# 결과: 41

DECR booth:1:current  # 체험 완료 시
# 결과: 40
```

**주요 연산**:
- `INCR`: 입장 확인 시 +1
- `DECR`: 체험 완료 시 -1
- `GET`: 현재 인원 조회

**특징**: 원자적 연산 보장 (동시성 안전)

**TTL**: 없음

---

### 4️⃣ 부스 메타 정보 (Hash)

**키**: `booth:{boothId}:meta`

**용도**: 부스 기본 정보 캐싱 (DB 조회 최소화)

**구조**:
```redis
HSET booth:1:meta name "테스트 부스"
HSET booth:1:meta capacity "50"
HSET booth:1:meta status "OPEN"

HGETALL booth:1:meta
# 결과: {
#   "name": "테스트 부스",
#   "capacity": "50",
#   "status": "OPEN"
# }
```

**주요 연산**:
- `HSET`: 메타 정보 저장
- `HGETALL`: 전체 정보 조회
- `HGET`: 특정 필드 조회

**TTL**: 10분 (600초)

---

### 5️⃣ FCM 토큰 캐시 (String)

**키**: `fcm:token:{userId}`

**용도**: 푸시 알림 발송용 FCM 토큰 캐싱

**구조**:
```redis
SET fcm:token:123 "fcm_device_token_abc123" EX 5184000
# TTL: 60일 (5184000초)
```

**주요 연산**:
- `SET ... EX`: 토큰 저장 (TTL 포함)
- `GET`: 토큰 조회

**TTL**: 60일 (5184000초)

**갱신**: 사용자 로그인 시 자동 갱신

---

### 6️⃣ 분산 락 (String)

**키**: `lock:user:{userId}:register`

**용도**: 동시 등록 방지 (대기 등록 시)

**구조**:
```redis
SET lock:user:123:register 1 NX EX 10
# NX: key가 없을 때만 설정
# EX 10: TTL 10초
```

**주요 연산**:
- `SET ... NX EX`: 락 획득
- `DEL`: 락 해제

**TTL**: 10초

**특징**: Simple Lock 방식 (Redisson 또는 Lua Script 사용 예정)

---

### 7️⃣ Soft Lock (임시 호출 처리 마킹) (Hash)

**키**: `temp:calling:{boothId}:{userId}`

**용도**: callNext 실패 지점 마킹 (MySQL-Redis 정합성 보장)

**구조**:
```redis
HSET temp:calling:1:123 boothId "1"
HSET temp:calling:1:123 userId "123"
HSET temp:calling:1:123 timestamp "1733900000"
HSET temp:calling:1:123 createdAt "1704096001000"

HGETALL temp:calling:1:123
# 결과: {
#   "boothId": "1",
#   "userId": "123",
#   "timestamp": "1733900000",       # 원래 등록 시각 (epoch seconds)
#   "createdAt": "1704096001000"     # Soft Lock 생성 시각 (milliseconds)
# }
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `boothId` | String | 부스 ID |
| `userId` | String | 사용자 ID |
| `timestamp` | String | 원래 등록 시각 (epoch seconds, 복구 시 사용) |
| `createdAt` | String | Soft Lock 생성 시각 (milliseconds) |

**생명주기**:
1. **생성**: Redis dequeue 직후 (callNext 시작)
2. **삭제 (성공)**: MySQL save 성공 시
3. **삭제 (실패)**: 배치가 Redis 롤백 후 삭제

**배치 보정** (`SoftLockRecoveryBatch`):
- **주기**: 1분마다 실행
- **로직**:
  ```
  1. Redis SCAN temp:calling:*
  2. 각 Soft Lock에 대해:
     - MySQL 확인 (CALLED 상태 존재?)
     - 존재: Soft Lock만 삭제 (정상 완료)
     - 없음: Redis 롤백 (ZADD + SADD, timestamp로 정확한 위치 복구) + Soft Lock 삭제
  ```
- **효율성**: O(실패 건수) - 정상 케이스 0건 스캔

**장점**:
- **timestamp 보존**: 원래 등록 시각으로 정확한 순서 복구
- **명시적 마킹**: 실패 지점 명확히 표시
- **배치 효율**: 실패 건수만 스캔 (vs MySQL CALLED 전체 조회)
- **디버깅 용이**: 어느 유저가 어느 부스에서 실패했는지 즉시 파악

**TTL**: 없음 (배치가 명시적 삭제)

**관련 문서**:
- [`/load-test/recovery-comparison/RESULT.md`](../../load-test/recovery-comparison/RESULT.md): 측정 결과 및 선택 이유
- [`/docs/design-decisions/distributed-consistency.md`](../design-decisions/distributed-consistency.md): 정합성 설계 결정

---

## ⏱️ TTL 정책

| 데이터 | TTL | 갱신 시점 | 이유 |
|--------|-----|----------|------|
| `queue:booth:*` | 없음 | - | 명시적 삭제 (호출/취소 시) |
| `user:*:active_booths` | 없음 | - | 명시적 삭제 (호출/취소 시) |
| `booth:*:current` | 없음 | - | 영구 유지 (부스 삭제 전까지) |
| `booth:*:meta` | 10분 | 부스 정보 변경 시 | DB 조회 최소화 |
| `fcm:token:*` | 60일 | 사용자 로그인 시 | 비활성 사용자 자동 정리 |
| `lock:user:*` | 10초 | - | 락 타임아웃 방지 |
| `temp:calling:*` | 없음 | - | 배치가 명시적 삭제 (정합성 보정용) |

---

## 📊 성능 특성

### 시간 복잡도
| 연산 | 복잡도 | 설명 |
|------|--------|------|
| `ZADD` (대기 등록) | O(log N) | 10만 건 기준 < 1ms |
| `ZRANK` (순번 조회) | O(log N) | 10만 건 기준 < 1ms |
| `ZPOPMIN` (호출) | O(log N) | 가장 앞 사람 제거 |
| `ZCARD` (대기자 수) | O(1) | 즉시 응답 |
| `INCR/DECR` (정원) | O(1) | < 0.1ms |
| `SCARD` (활성 부스 수) | O(1) | 즉시 응답 |

### 메모리 최적화
- 부스 메타 TTL 10분 → 불필요 시 자동 삭제
- FCM 토큰 TTL 60일 → 비활성 사용자 자동 정리

---

## 🔐 동시성 제어 & 정합성

### 분산 락 설계
- **대상**: 대기 등록, 호출, 입장 확인 등
- **방식**: Redisson 또는 Lua Script

### MySQL-Redis 정합성
- **문제**: 호출 시 Redis 제거 → MySQL 저장 사이 실패 가능
- **해결**: Soft Lock (임시 상태 키) + 배치 보정

**상세 내용**:
- [concurrency-control.md](../design-decisions/concurrency-control.md)
- [distributed-consistency.md](../design-decisions/distributed-consistency.md)

---

## 🚨 장애 복구

### Redis 장애 시
- **대기 등록/조회 불가**: 503 Service Unavailable 반환
- **복구 후**: MySQL에 `CALLED` 이전 데이터 없음 → **대기열 초기화**

### Redis 재구성
```sql
-- MySQL에서 CALLED 이전 상태는 저장하지 않으므로 재구성 불가
-- Redis 장애 = 대기열 소실 (재등록 필요)
```

**대응 방안**:
- Redis Cluster 구성 (고가용성)
- AOF 활성화 (데이터 영속성)

---

**Last Updated**: 2025-12-17
**Version**: 1.0