# Issue #4: MySQL-Redis 정합성 보장 방식 비교 - 측정 결과

## 📊 Executive Summary

**결론: Soft Lock 방식 채택**

TransactionalEventListener와 Soft Lock 두 가지 접근법을 정량적으로 비교한 결과, **Soft Lock 방식이 복구 정확성, 배치 효율성, 구조적 단순성 모든 면에서 우수**하다고 판단.

---

## 🎯 비교 대상

### 1. TransactionalEventListener 방식

**핵심 아이디어:**
- MySQL 커밋 성공 → `@TransactionalEventListener(AFTER_COMMIT)` → Redis 작업
- "MySQL이 성공하면 Redis도 따라오게 하자"

**구조:**
```
1. Redis peek (제거하지 않음)
2. MySQL save (@Transactional)
3. AFTER_COMMIT 이벤트 발행
4. TransactionalEventListener에서 Redis dequeue
5. 실패 시 배치가 MySQL CALLED 조회 → Redis 제거
```

**문제점:**
- AFTER_COMMIT도 실패 가능 → 결국 배치 필요
- Peek 메커니즘으로 인한 Race Condition 가능성
- 역할 전도: Redis가 Source of Truth인데 MySQL 먼저 저장

---

### 2. Soft Lock 방식

**핵심 아이디어:**
- Redis dequeue 즉시 실행 → Soft Lock 마킹 → MySQL save
- 실패 지점을 Redis 임시 키로 명시적 표시

**구조:**
```
1. Redis dequeue (즉시 제거, 원자적)
2. Soft Lock 생성 (temp:calling:{boothId}:{userId}, timestamp 보존)
3. MySQL save (@Transactional)
4. 성공: Soft Lock 삭제
5. 실패: Soft Lock 남김 → 배치가 timestamp로 롤백
```

**장점:**
- 자연스러운 흐름 유지 (Redis → MySQL)
- Soft Lock으로 실패 지점 명시적 표시
- timestamp 보존으로 정확한 순서 복구

---

## 📈 측정 결과

### 테스트 환경
- **테스트 도구:** Port 레벨 Failure Injection (Mockito.spy() + Real MySQL/Redis)
- **Infrastructure:** TestContainers (MySQL 8.0, Redis 7.2)
- **테스트 파일:** `RecoveryApproachComparisonTest.java`

---

### 측정 1: 복구 정확성

**시나리오:**
- 대기열에 5명 등록 (rank: 1, 2, 3, 4, 5)
- user1, user2 callNext 성공 (제거)
- user3가 이제 rank 1 (원래는 rank 3)
- user3 callNext 시도 → MySQL 실패
- 배치 복구 실행
- **질문: user3가 어느 위치로 복구되는가?**

**결과:**

| 방식 | 원래 rank | callNext 실패 시점 rank | 복구 후 rank | 평가 |
|------|-----------|-------------------------|--------------|------|
| **Soft Lock** | 3 | 1 | **1** ✅ | dequeue 시점 timestamp 보존 |
| EventListener | 3 | 1 | N/A | Peek만 하므로 복구 불필요 (MySQL만 롤백) |

**분석:**
- **Soft Lock**: dequeue 시점의 timestamp를 Soft Lock에 보존 → 정확한 위치 복구
- **EventListener**: Peek만 하므로 Redis 변경 없음 → 복구 불필요하지만 Peek-Save gap 동안 Race Condition 가능

**승자: Soft Lock** (timestamp 보존으로 정확한 순서 복구)

---

### 측정 2: 배치 스캔 효율

**시나리오:**
- 100명이 최근 3분 내에 callNext 성공 (CALLED 상태, MySQL 저장 완료)
- 1명이 callNext 실패 (Soft Lock 생성)
- 각 배치 실행 및 시간 측정

**결과:**

| 방식 | 스캔 대상 | 스캔 건수 | 소요 시간 | 시간 복잡도 |
|------|-----------|-----------|-----------|-------------|
| **Soft Lock** | Redis SCAN `temp:calling:*` | 1건 (실패만) | **5ms** | O(실패 건수) |
| **EventListener** | MySQL `SELECT WHERE status=CALLED AND calledAt > now-5min` | 100건 (전체) | **64ms** | O(최근 CALLED 수) |

**쿼리 비교:**

**Soft Lock:**
```bash
# Redis SCAN
SCAN 0 MATCH temp:calling:* COUNT 100
→ 1건만 반환 (실패한 경우만)

# MySQL 확인 (각 Soft Lock마다)
SELECT EXISTS(SELECT 1 FROM waiting WHERE user_id=? AND booth_id=? AND status='CALLED')
→ 인덱스 사용 (user_id, booth_id, status)
```

**EventListener:**
```sql
-- MySQL Full Scan
SELECT * FROM waiting
WHERE status = 'CALLED'
  AND called_at > DATE_SUB(NOW(), INTERVAL 5 MINUTE)
→ 100건 조회 후 각각 Redis 확인

-- Redis 확인 (각 CALLED마다)
ZSCORE queue:booth:{boothId} {userId}
```

**분석:**
- **Soft Lock**: 실패 건수만큼만 스캔 (정상 케이스 0건)
- **EventListener**: 최근 CALLED 전체 조회 후 Redis 대조

**승자: Soft Lock** (12.8배 빠름, O(실패 건수) vs O(전체))

---

### 측정 3: 복구 시간

**시나리오:**
- callNext 실패 발생
- 배치 실행 (실전에서는 1분 후 스케줄러 실행)
- 복구 완료까지 총 시간 측정

**결과:**

| 방식 | 실패 감지 방법 | 배치 처리 시간 | 총 복구 시간 (실전 1분 포함) | 평가 |
|------|----------------|----------------|------------------------------|------|
| **Soft Lock** | Soft Lock 존재 = 실패 | 25ms | **~1분 25ms** | 즉시 감지 가능 |
| **EventListener** | MySQL CALLED 있는데 Redis에도 있는 케이스 | 64ms | **~1분 64ms** | MySQL 조회 필요 |

**분석:**
- 실전 복구 시간 = 배치 주기(1분) + 배치 처리 시간
- **Soft Lock**: Redis SCAN만으로 즉시 실패 감지
- **EventListener**: MySQL 조회 후 Redis 대조 필요

**승자: Soft Lock** (감지가 직관적이고 처리 빠름)

---

## 📝 코드 복잡도 비교

### LOC (Lines of Code)

| 구성 요소 | TransactionalEventListener | Soft Lock |
|-----------|---------------------------|-----------|
| **Service** | 160 lines (V2EventListener) | 168 lines (V3SoftLock) |
| **Batch** | 96 lines (EventListenerRecovery) | 132 lines (SoftLockRecovery) |
| **Port 추가 메서드** | `peekFirst()` + 구현 (~30 lines) | - |
| **Repository 추가** | `findRecentByStatus()` | `existsByUserIdAndBoothIdAndStatus()` |
| **총합** | ~286 lines | ~300 lines |

**차이: 약 14줄 (거의 동일)**

---

### 사이클로매틱 복잡도

**TransactionalEventListener 방식:**
- Service: 복잡도 낮음 (Peek → Save → Event)
- EventListener: try-catch 중첩, 예외 처리 복잡
- Batch: 이중 조회 (MySQL → Redis) + 상태 확인

**Soft Lock 방식:**
- Service: Soft Lock 생성/삭제 추가, 약간 복잡
- Batch: SCAN → MySQL 확인 → 롤백, 단순 선형

**평가: 비슷함** (Soft Lock이 약간 더 직관적)

---

### 아키텍처 복잡도

**TransactionalEventListener 방식:**
- ⚠️ **역할 전도**: Redis가 Source of Truth인데 MySQL 먼저 저장
- ⚠️ **Peek 메커니즘**: 새로운 Port 메서드 필요 (`peekFirst()`)
- ⚠️ **Race Condition**: Peek-Save gap 동안 동시성 문제 가능
- ⚠️ **이벤트 기반 복잡도**: AFTER_COMMIT 이벤트 흐름 추적 어려움

**Soft Lock 방식:**
- ✅ **자연스러운 흐름**: Redis → MySQL (Source of Truth 순서 유지)
- ✅ **명시적 마킹**: Soft Lock으로 실패 지점 명확히 표시
- ✅ **원자적 연산**: dequeue 즉시 실행 (Redis 내부 원자성)

**승자: Soft Lock** (아키텍처적으로 더 깔끔)

---

## 🏆 종합 평가

| 평가 항목 | TransactionalEventListener | Soft Lock | 승자 |
|-----------|---------------------------|-----------|------|
| **복구 정확성** | Peek이라 복구 불필요<br/>(but Race Condition 가능) | timestamp 보존 → 정확한 위치 복구 | **Soft Lock** |
| **배치 스캔 효율** | O(최근 CALLED 수)<br/>64ms (100건) | O(실패 건수)<br/>5ms (1건) | **Soft Lock** |
| **복구 시간** | ~1분 64ms<br/>MySQL 조회 필요 | ~1분 25ms<br/>즉시 감지 | **Soft Lock** |
| **LOC** | ~286 lines | ~300 lines | **비슷함** |
| **사이클로매틱 복잡도** | 이벤트 흐름 복잡 | 선형적 흐름 | **Soft Lock** |
| **아키텍처 단순성** | 역할 전도, Peek gap | 자연스러운 흐름 | **Soft Lock** |

---

## 🔍 TransactionalEventListener의 치명적 함정

### 문제 1: AFTER_COMMIT도 실패한다

**착각:**
> "MySQL 커밋 성공하면 Redis도 따라오게 하면 되지 않나?"

**현실:**
```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleCallNextEvent(CallNextEvent event) {
    // ⚠️ 여기도 실패 가능!
    queueCachePort.dequeue(event.boothId());  // Redis 장애, 네트워크 오류
    queueCachePort.removeUserActiveBooth(...); // 예외 발생
}
```

**결과:**
- MySQL: CALLED 상태로 커밋 완료 ✅
- Redis: 여전히 대기열에 존재 ❌
- **결국 배치 보정 필요** → TransactionalEventListener의 의미 퇴색

---

### 문제 2: 역할 전도

**Redis의 역할:**
- 대기열의 **Source of Truth**
- 실시간 순서 보장
- 원자적 연산 보장

**TransactionalEventListener 방식:**
```
MySQL 먼저 저장 → Redis는 나중에 따라옴
```

**문제점:**
- Redis가 주인인데 MySQL이 먼저 움직임
- "누가 진짜 주인인가?" 혼란
- 아키텍처 일관성 저하

---

### 문제 3: Peek-Save Gap

```java
// 1. Peek: 조회만 (제거 안 함)
QueueItem item = queueCachePort.peekFirst(boothId);

// ⚠️ Gap: 이 사이에 다른 트랜잭션이 끼어들 수 있음
// - 분산 락이 필요하지만 Peek 동안 락을 잡아야 함
// - 락 구간이 MySQL 트랜잭션까지 확장됨

// 2. MySQL Save
waitingRepositoryPort.save(waiting);
```

**문제점:**
- Race Condition 가능성
- 분산 락 필요 → Soft Lock과 동일한 복잡도
- Peek 메서드 추가 필요 (QueueCachePort 확장)

---

## ✅ Soft Lock 방식의 우수성

### 1. 자연스러운 흐름 유지

```java
// Redis → MySQL (Source of Truth 순서)
QueueItem item = queueCachePort.dequeue(boothId);  // 1. 즉시 제거
createSoftLock(key, data);                          // 2. 실패 지점 마킹
waitingRepositoryPort.save(waiting);                // 3. MySQL 저장
deleteSoftLock(key);                                // 4. 성공 시 마킹 제거
```

**장점:**
- Redis가 주도권 유지 (Source of Truth)
- 아키텍처 일관성 유지

---

### 2. 명시적 실패 마킹

**Soft Lock 구조:**
```redis
temp:calling:123:456
{
  "boothId": "123",
  "userId": "456",
  "timestamp": "1704096000",  // ← 원래 순서 보존!
  "createdAt": "1704096001"
}
```

**장점:**
- Redis SCAN만으로 즉시 실패 감지
- timestamp 보존 → 정확한 위치 복구
- 디버깅 용이 (어느 유저가 어느 부스에서 실패했는지 명확)

---

### 3. 배치 효율성

```java
// Soft Lock 방식: O(실패 건수)
SCAN 0 MATCH temp:calling:* COUNT 100
→ 실패 건수만큼만 스캔 (정상 케이스: 0건)

// EventListener 방식: O(최근 CALLED 수)
SELECT * FROM waiting WHERE status='CALLED' AND calledAt > now-5min
→ 최근 5분 전체 CALLED 조회 (정상 케이스도 조회)
```

**측정 결과: 12.8배 차이 (5ms vs 64ms)**

---

### 4. timestamp 보존으로 정확한 복구

**시나리오:**
```
초기: [user1(rank1), user2(rank2), user3(rank3)]
user1 dequeue → [user2(rank1), user3(rank2)]
user2 dequeue → [user3(rank1)]
user3 callNext → MySQL 실패!
```

**Soft Lock 복구:**
```java
// Soft Lock에 user3의 dequeue 시점 timestamp 보존됨
rollbackRedis(boothId, userId, timestamp);
→ user3를 dequeue 직전의 timestamp로 복구
→ 정확히 rank 1로 복구! ✅
```

**EventListener 복구:**
```
Peek만 했으므로 Redis 변경 없음
→ 복구 불필요 (MySQL만 롤백)
→ 하지만 Peek-Save gap에 Race Condition 가능
```

---

## 🚀 최종 결론

### Soft Lock 방식 채택 이유

1. **복구 정확성**: timestamp 보존으로 정확한 순서 복구
2. **배치 효율성**: 12.8배 빠른 스캔 (5ms vs 64ms)
3. **아키텍처 일관성**: Redis → MySQL 흐름 유지 (Source of Truth)
4. **명시적 마킹**: 실패 지점 명확히 표시
5. **디버깅 용이**: Soft Lock 키로 즉시 문제 파악

### TransactionalEventListener 방식을 선택하지 않은 이유

1. **AFTER_COMMIT도 실패** → 결국 배치 필요 (본질적 해결 X)
2. **역할 전도**: MySQL 먼저 저장 → Redis 나중 (아키텍처 혼란)
3. **Peek-Save Gap**: Race Condition 가능성
4. **배치 비효율**: 최근 CALLED 전체 조회 (O(N))

---

## 📚 참고 자료

### 테스트 파일
- `RecoveryApproachComparisonTest.java`: 정량적 측정 테스트
- `CallNextConsistencyTest.java`: Port Failure Injection 증명

### 구현 파일
- `CallNextServiceV2EventListener.java`: TransactionalEventListener 방식
- `CallNextServiceV3SoftLock.java`: Soft Lock 방식
- `EventListenerRecoveryBatch.java`: EventListener 배치
- `SoftLockRecoveryBatch.java`: Soft Lock 배치

### 문서
- [`README.md`](README.md): 비교에 사용한 실험용 구현 코드 (이 디렉토리)
- [`/docs/design-decisions/distributed-consistency.md`](../../docs/design-decisions/distributed-consistency.md): 설계 결정 (Outbox 기각 사유 포함)

---

**작성일:** 2026-01-03
**측정 환경:** TestContainers (MySQL 8.0, Redis 7.2)
**측정 방법:** Port 레벨 Failure Injection (Real Infrastructure)