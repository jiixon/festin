# Distributed Consistency - 분산 정합성 설계

## 🎯 목적

**MySQL-Redis 간 데이터 정합성**을 보장하고, 분산 트랜잭션 실패 시 복구 전략을 수립합니다.

---

## 🔍 정합성 문제 발생 지점

### 1️⃣ 다음 사람 호출 (CallNextService)

**파일**: `CallNextService.java:74-99`

**작업 흐름**:
```java
@Transactional  // MySQL 트랜잭션 시작
public CallResult callNext(Long boothId) {
    // 1. Redis에서 dequeue
    Long userId = queueCachePort.dequeue(boothId);  // ✅ Redis 변경

    // 2. Redis에서 활성 부스 제거
    queueCachePort.removeUserActiveBooth(userId, boothId);  // ✅ Redis 변경

    // 3. MySQL에 저장
    Waiting waiting = Waiting.builder()...build();
    Waiting savedWaiting = waitingRepositoryPort.save(waiting);  // ⚠️ MySQL 변경

    // 4. 푸시 알림 발송 (비동기)
    notificationPort.sendCallNotification(...);

    return new CallResult(...);
}
```

**문제 시나리오**:
```
1. Redis dequeue 성공 (사용자 123 제거)
2. Redis removeActiveBooth 성공
3. MySQL save 실패 (DB 장애, 네트워크 끊김, 예외 발생)
   ❌ 트랜잭션 롤백
   ❌ Redis는 이미 변경됨 (되돌릴 수 없음)

결과:
- Redis: 사용자 123은 대기열에서 사라짐
- MySQL: 호출 이력 없음
- 사용자: 대기열에서 사라졌지만 호출받지 못함 😱
```

**심각도**: 🔴 **Critical** - 사용자 대기 소실

---

### 2️⃣ 입장 확인 (ConfirmEntranceService)

**파일**: `ConfirmEntranceService.java:50-56`

**작업 흐름**:
```java
@Transactional  // MySQL 트랜잭션
public EntranceResult confirmEntrance(Long boothId, Long waitingId) {
    // 1. MySQL 조회 및 상태 변경
    Waiting waiting = waitingRepositoryPort.findById(waitingId);
    waiting.enter();  // CALLED → ENTERED
    Waiting updatedWaiting = waitingRepositoryPort.save(waiting);  // ✅ MySQL 변경

    // 2. Redis 정원 +1
    boothCachePort.incrementCurrentCount(boothId);  // ⚠️ Redis 변경 (트랜잭션 외부)

    return new EntranceResult(...);
}
```

**문제 시나리오**:
```
1. MySQL save 성공 (ENTERED 상태 저장)
2. Redis INCR 실패 (Redis 장애, 네트워크 끊김)
   ❌ Redis 정원 카운트 증가 안 됨

결과:
- MySQL: ENTERED 상태
- Redis: 정원 카운트 부정확 (실제보다 1명 적음)
- 부스: 정원 여유가 있다고 잘못 판단 → 초과 호출 가능
```

**심각도**: ⚠️ **High** - 정원 관리 부정확

---

### 3️⃣ 체험 완료 (CompleteExperienceService)

**파일**: `CompleteExperienceService.java:50-56`

**문제**: 입장 확인과 동일 (MySQL save → Redis DECR)

**심각도**: ⚠️ **High**

---

## 🛡️ 해결 방안

### ❌ Outbox 패턴을 쓰지 않는 이유

**Outbox 패턴이란?**
```sql
-- MySQL에 이벤트 저장
INSERT INTO outbox_events (event_type, payload, status)
VALUES ('USER_CALLED', '{"userId": 123, "boothId": 1}', 'PENDING');

-- 별도 Worker가 주기적으로 처리
SELECT * FROM outbox_events WHERE status = 'PENDING';
-- Redis 반영
-- 성공 시 status = 'COMPLETED'
```

**장점**: 완벽한 정합성 보장 (MySQL → Redis 순서 보장)

**단점**:
- **복잡도 증가**: Outbox 테이블, Worker, 이벤트 발행 로직 필요
- **지연 발생**: 실시간이 아닌 배치 처리 (수초 지연)
- **과도한 설계**: 금전적 손실이 없는 대기 시스템에는 과함

**결론**: Festin은 **금전 거래 없음** → Outbox는 과도 → **Soft Lock + 배치 보정** 채택

---

### ✅ Soft Lock (임시 상태 키) 설계

**개념**: Redis 변경 전에 **임시 키**를 생성하여, 실패 시 배치 보정으로 복구

---

#### 1️⃣ 다음 사람 호출 (CallNextService)

**개선된 흐름**:
```java
@Transactional
public CallResult callNext(Long boothId) {
    // [Phase 1] Soft Lock 생성
    String tempKey = "temp:calling:" + boothId + ":" + System.currentTimeMillis();
    Long userId = queueCachePort.peekFirst(boothId);  // dequeue 대신 peek
    softLockPort.create(tempKey, userId, boothId);  // TTL 60초

    try {
        // [Phase 2] Redis 변경
        queueCachePort.dequeue(boothId);
        queueCachePort.removeUserActiveBooth(userId, boothId);

        // [Phase 3] MySQL 저장
        Waiting waiting = Waiting.builder()...build();
        Waiting savedWaiting = waitingRepositoryPort.save(waiting);

        // [Phase 4] Soft Lock 삭제 (성공)
        softLockPort.delete(tempKey);

        return new CallResult(...);

    } catch (Exception e) {
        // 실패 시 Soft Lock은 남음 (배치가 처리)
        throw e;
    }
}
```

**Soft Lock 데이터 구조** (Redis Hash):
```redis
HSET temp:calling:1:1733900000 userId "123"
HSET temp:calling:1:1733900000 boothId "1"
HSET temp:calling:1:1733900000 timestamp "1733900000"
EXPIRE temp:calling:1:1733900000 60  # TTL 60초
```

**배치 보정 (1분마다)**:
```java
@Scheduled(fixedDelay = 60000)
public void recoverCallingFailures() {
    // 1. 임시 키 조회 (60초 지난 것만)
    Set<String> tempKeys = redis.keys("temp:calling:*");

    for (String tempKey : tempKeys) {
        Long userId = redis.hget(tempKey, "userId");
        Long boothId = redis.hget(tempKey, "boothId");

        // 2. MySQL에 호출 이력 있는지 확인
        Optional<Waiting> waiting = waitingRepository
            .findByUserIdAndBoothIdAndStatus(userId, boothId, CALLED);

        if (waiting.isPresent()) {
            // MySQL 저장 성공했음 → Soft Lock만 삭제
            redis.del(tempKey);
        } else {
            // MySQL 저장 실패 → Redis 롤백 (대기열 복원)
            redis.zadd("queue:booth:" + boothId, timestamp, userId);
            redis.sadd("user:" + userId + ":active_booths", boothId);
            redis.del(tempKey);
        }
    }
}
```

---

#### 2️⃣ 입장 확인 (ConfirmEntranceService)

**개선된 흐름**:
```java
@Transactional
public EntranceResult confirmEntrance(Long boothId, Long waitingId) {
    // [Phase 1] Soft Lock 생성
    String tempKey = "temp:entrance:" + waitingId;
    softLockPort.create(tempKey, waitingId, boothId);  // TTL 60초

    try {
        // [Phase 2] MySQL 상태 변경
        Waiting waiting = waitingRepositoryPort.findById(waitingId);
        waiting.enter();
        Waiting updatedWaiting = waitingRepositoryPort.save(waiting);

        // [Phase 3] Redis 정원 +1
        boothCachePort.incrementCurrentCount(boothId);

        // [Phase 4] Soft Lock 삭제 (성공)
        softLockPort.delete(tempKey);

        return new EntranceResult(...);

    } catch (Exception e) {
        // 실패 시 Soft Lock은 남음 (배치가 처리)
        throw e;
    }
}
```

**배치 보정**:
```java
@Scheduled(fixedDelay = 60000)
public void recoverEntranceFailures() {
    Set<String> tempKeys = redis.keys("temp:entrance:*");

    for (String tempKey : tempKeys) {
        Long waitingId = redis.hget(tempKey, "waitingId");
        Long boothId = redis.hget(tempKey, "boothId");

        // MySQL에서 상태 확인
        Optional<Waiting> waiting = waitingRepository.findById(waitingId);

        if (waiting.isPresent() && waiting.get().getStatus() == ENTERED) {
            // MySQL 저장 성공 → Redis 보정
            int expectedCount = waitingRepository
                .countByBoothIdAndStatusIn(boothId, List.of(ENTERED));
            redis.set("booth:" + boothId + ":current", expectedCount);
            redis.del(tempKey);
        } else {
            // MySQL 저장 실패 → Soft Lock만 삭제
            redis.del(tempKey);
        }
    }
}
```

---

#### 3️⃣ 체험 완료 (CompleteExperienceService)

**개선된 흐름**: 입장 확인과 유사

**배치 보정**: COMPLETED 상태 개수로 Redis 정원 재계산

---

## 📊 Soft Lock vs Outbox 비교

| 항목 | Soft Lock | Outbox |
|------|-----------|--------|
| **복잡도** | 낮음 | 높음 |
| **정합성** | Eventually Consistent (최종 일관성) | Strong Consistent (강한 일관성) |
| **지연** | 최대 1분 (배치 주기) | 수초 내외 |
| **적용 대상** | 비금전 시스템 | 금전 거래, 결제 |
| **비용** | 낮음 (Redis만 추가) | 높음 (Outbox 테이블, Worker) |

---

## 🚨 실패 시나리오 및 복구

### Scenario 1: MySQL 장애 (호출 중)
```
1. Redis dequeue 성공
2. MySQL save 실패 ❌
3. Soft Lock 남음
4. 배치 보정 (1분 후)
   → MySQL 이력 없음 확인
   → Redis 대기열 복원
   → 사용자는 다시 대기열 앞으로
```

**사용자 경험**: 1분 뒤 순번 복원 (약간의 지연, 큰 문제 아님)

---

### Scenario 2: Redis 장애 (입장 확인 중)
```
1. MySQL save 성공 (ENTERED)
2. Redis INCR 실패 ❌
3. Soft Lock 남음
4. 배치 보정 (1분 후)
   → MySQL 상태 ENTERED 확인
   → Redis 정원 재계산 (DB 기준)
   → 정확한 정원 복구
```

**사용자 경험**: 영향 없음 (백그라운드 보정)

---

### Scenario 3: 배치 실패 (극단적)
```
배치 서버 장애로 1시간 동안 보정 안 됨
→ Soft Lock TTL 60초 지나면 자동 삭제
→ 데이터는 그대로 남음 (Redis/MySQL 불일치)

복구 방법:
1. 수동 스크립트 실행
   - MySQL 전체 ENTERED 개수 조회
   - Redis 정원 재설정
2. 배치 재시작 시 자동 복구
```

---

## 🔐 Soft Lock 키 설계

| 작업 | Soft Lock 키 | TTL | 포함 데이터 |
|------|-------------|-----|-----------|
| **호출** | `temp:calling:{boothId}:{timestamp}` | 60초 | userId, boothId, timestamp |
| **입장** | `temp:entrance:{waitingId}` | 60초 | waitingId, boothId |
| **완료** | `temp:complete:{waitingId}` | 60초 | waitingId, boothId |

---

## 📝 구현 우선순위

1. **Phase 1**: CallNextService Soft Lock 적용 (가장 중요)
2. **Phase 2**: 배치 보정 로직 구현
3. **Phase 3**: 입장/완료 Soft Lock 적용
4. **Phase 4**: 모니터링 및 알림 (Soft Lock 누적 시 경고)

---

## 🧪 테스트 전략

### 정합성 테스트
```java
@Test
void MySQL_저장_실패_시_Redis_롤백() {
    // Given
    Long boothId = 1L;
    Long userId = 123L;
    queueCachePort.enqueue(boothId, userId, LocalDateTime.now());

    // When: MySQL 저장 실패 시뮬레이션
    doThrow(new DataAccessException("DB Error"))
        .when(waitingRepositoryPort).save(any());

    assertThatThrownBy(() -> callNextService.callNext(boothId))
        .isInstanceOf(DataAccessException.class);

    // Then: 배치 보정 실행
    batchRecoveryService.recoverCallingFailures();

    // Verify: Redis 대기열 복원 확인
    Optional<Integer> position = queueCachePort.getPosition(boothId, userId);
    assertThat(position).isPresent();
    assertThat(position.get()).isEqualTo(1);
}
```

---

## 📈 모니터링

### 지표 수집
- **Soft Lock 개수**: `redis.keys("temp:*").size()`
- **배치 보정 성공률**: `recovered / total`
- **배치 실행 시간**: `execution_time`

### 알림 조건
- Soft Lock 10개 이상 누적 → Slack 알림
- 배치 보정 실패율 > 10% → 긴급 알림

---

**Last Updated**: 2025-12-17
**Version**: 1.0