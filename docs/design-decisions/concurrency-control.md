# Concurrency Control - 동시성 제어 설계

## 🎯 목적

분산 환경에서 **동시 요청으로 인한 Race Condition**을 방지하고, 비즈니스 규칙을 정확히 보장합니다.

---

## 🔍 동시성 문제 발생 지점

### 1️⃣ 대기 등록 (EnqueueService)

**파일**: `EnqueueService.java:87-94`

**문제 시나리오**:
```java
// 사용자 A가 부스 1, 2에 대기 중
// 요청 1: 부스 3 등록 시도
// 요청 2: 부스 4 등록 시도 (거의 동시에)

int activeCount = queueCachePort.getUserActiveBoothCount(userId);  // 요청 1, 2 모두 "2" 조회
maxWaitingPolicy.validate(activeCount);  // 요청 1, 2 모두 통과
// ⚠️ 여기서 Race Condition!
queueCachePort.enqueue(boothId, userId, now);  // 요청 1 등록 (총 3개)
queueCachePort.enqueue(boothId, userId, now);  // 요청 2 등록 (총 4개) ❌
```

**결과**: 최대 2개 제한 위반 (4개 부스 대기 가능)

**심각도**: ⚠️ **High** - 비즈니스 규칙 위반

---

### 2️⃣ 다음 사람 호출 (CallNextService)

**파일**: `CallNextService.java:71-76`

**문제 시나리오**:
```java
// 부스 정원: 50명, 현재 인원: 49명
// 스태프 A, B가 동시에 "다음 사람 호출" 클릭

int currentCount = boothCachePort.getCurrentCount(boothId);  // A, B 모두 "49" 조회
booth.validateForCalling(currentCount);  // A, B 모두 통과 (49 < 50)
// ⚠️ 여기서 Race Condition!
Long userId1 = queueCachePort.dequeue(boothId);  // A가 사용자 1 호출
Long userId2 = queueCachePort.dequeue(boothId);  // B가 사용자 2 호출
// 입장 확인 시 currentCount = 51 ❌
```

**결과**: 부스 정원 초과 (51명)

**심각도**: ⚠️ **High** - 부스 정원 규칙 위반

---

### 3️⃣ 입장 확인 (ConfirmEntranceService)

**파일**: `ConfirmEntranceService.java:50-56`

**문제 시나리오**:
```java
// 동일한 waitingId에 대해 스태프가 실수로 2번 클릭

// 요청 1, 2 모두 동시에 진행
Waiting waiting = waitingRepositoryPort.findById(waitingId);  // 요청 1, 2 모두 CALLED 상태 조회
waiting.enter();  // 요청 1, 2 모두 ENTERED로 변경
waitingRepositoryPort.save(waiting);  // 요청 1, 2 모두 저장
// ⚠️ 여기서 Race Condition!
boothCachePort.incrementCurrentCount(boothId);  // 요청 1: +1
boothCachePort.incrementCurrentCount(boothId);  // 요청 2: +1 (중복) ❌
```

**결과**: Redis 정원 카운트 부정확 (+2 증가)

**심각도**: ⚠️ **Medium** - 정원 카운트 오차 발생

---

### 4️⃣ 체험 완료 (CompleteExperienceService)

**파일**: `CompleteExperienceService.java:50-56`

**문제 시나리오**: 입장 확인과 동일 (DECR 중복 실행)

**심각도**: ⚠️ **Medium**

---

## 🛡️ 해결 방안

### 방안 1: Redisson 분산 락

**장점**:
- 구현 간단 (라이브러리 사용)
- Pub/Sub 기반 효율적 대기 (Spin Lock 없음)
- 자동 Lock 갱신 (Watchdog)

**단점**:
- 외부 라이브러리 의존
- Redis 장애 시 Lock 불가

**구현 예시**:
```java
@Service
public class EnqueueService {
    private final RedissonClient redissonClient;

    public EnqueueResult enqueue(EnqueueCommand command) {
        String lockKey = "lock:user:" + command.userId() + ":register";
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 3초 대기, 10초 TTL
            if (lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                // 비즈니스 로직 (activeCount 체크 → enqueue)
                return executeEnqueue(command);
            } else {
                throw new LockAcquisitionFailedException();
            }
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
```

---

### 방안 2: Lua Script (원자적 연산)

**장점**:
- Redis 네이티브 기능 (외부 의존 없음)
- 네트워크 왕복 최소화 (단일 요청)
- 완벽한 원자성 보장

**단점**:
- 스크립트 작성/관리 복잡도
- 디버깅 어려움

**구현 예시**:
```lua
-- enqueue.lua
local userId = ARGV[1]
local boothId = ARGV[2]
local timestamp = ARGV[3]

-- 1. 활성 부스 개수 확인
local activeCount = redis.call('SCARD', 'user:' .. userId .. ':active_booths')
if activeCount >= 2 then
    return {err = 'MAX_WAITING_EXCEEDED'}
end

-- 2. 중복 등록 체크
local existingRank = redis.call('ZRANK', 'queue:booth:' .. boothId, userId)
if existingRank then
    return {err = 'ALREADY_REGISTERED'}
end

-- 3. 대기열 추가
redis.call('ZADD', 'queue:booth:' .. boothId, timestamp, userId)

-- 4. 활성 부스 추가
redis.call('SADD', 'user:' .. userId .. ':active_booths', boothId)

return {ok = 'SUCCESS'}
```

**Java 호출**:
```java
@Service
public class EnqueueService {
    private final RedisScript<String> enqueueScript;

    public EnqueueResult enqueue(EnqueueCommand command) {
        String result = redisTemplate.execute(
            enqueueScript,
            Collections.emptyList(),
            command.userId().toString(),
            command.boothId().toString(),
            String.valueOf(System.currentTimeMillis())
        );

        if ("MAX_WAITING_EXCEEDED".equals(result)) {
            throw new MaxWaitingExceededException();
        }
        // ...
    }
}
```

---

## 📊 방안 비교

| 항목 | Redisson | Lua Script |
|------|----------|------------|
| **구현 난이도** | 쉬움 | 중간 |
| **성능** | 중간 (Lock 대기) | 우수 (단일 요청) |
| **원자성** | 보장 | 완벽 보장 |
| **외부 의존성** | 있음 | 없음 |
| **디버깅** | 쉬움 | 어려움 |
| **적용 범위** | 모든 서비스 | Redis 작업만 |

---

## ✅ 채택 전략

### 대기 등록 (EnqueueService)
**선택**: **Lua Script**

**이유**:
- Redis 작업만으로 완결 (activeCount 체크 → enqueue → addActiveBooth)
- 네트워크 왕복 최소화 (성능 중요)
- 외부 의존성 없음

---

### 다음 사람 호출 (CallNextService)
**선택**: **Redisson 분산 락**

**이유**:
- Redis dequeue + MySQL save (복합 작업)
- Lua Script로 MySQL 제어 불가
- Lock 범위가 명확 (부스별)

**Lock 키**: `lock:booth:{boothId}:call`

---

### 입장 확인 / 체험 완료
**선택**: **MySQL 비관적 락 (Pessimistic Lock)**

**이유**:
- DB 조회 → 상태 변경 → Redis 증감
- DB 레벨에서 중복 실행 방지
- Redis 분산 락 불필요

**구현**:
```java
@Repository
public interface WaitingJpaRepository extends JpaRepository<WaitingEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM WaitingEntity w WHERE w.id = :id")
    Optional<WaitingEntity> findByIdWithLock(@Param("id") Long id);
}
```

---

## 🔐 Lock 키 설계

| 서비스 | Lock 키 | TTL | 범위 |
|--------|---------|-----|------|
| **대기 등록** | (Lua Script 사용) | - | - |
| **다음 사람 호출** | `lock:booth:{boothId}:call` | 10초 | 부스별 |
| **입장 확인** | (MySQL Lock 사용) | - | - |
| **체험 완료** | (MySQL Lock 사용) | - | - |

---

## 🚨 예외 처리

### Lock 획득 실패 시
```java
if (!lock.tryLock(3, 10, TimeUnit.SECONDS)) {
    throw new LockAcquisitionFailedException(
        "다른 요청이 처리 중입니다. 잠시 후 다시 시도해주세요."
    );
}
```

**HTTP 응답**: `409 Conflict`

---

### Lock 타임아웃
- **TTL**: 10초 (충분한 시간 확보)
- **Watchdog**: Redisson 자동 갱신 활성화
- **Dead Lock 방지**: finally에서 반드시 unlock

---

## 📝 구현 우선순위

1. **Phase 1**: CallNextService에 Redisson 적용 (가장 심각)
2. **Phase 2**: EnqueueService에 Lua Script 적용
3. **Phase 3**: 입장/완료에 MySQL Lock 적용

---

## 🧪 테스트 전략

### 동시성 테스트
```java
@Test
void 동시_대기_등록_테스트() throws InterruptedException {
    int threadCount = 10;
    ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; i++) {
        executorService.submit(() -> {
            try {
                enqueueService.enqueue(EnqueueCommand.of(userId, boothId));
            } finally {
                latch.countDown();
            }
        });
    }

    latch.await();

    // 검증: 활성 부스 개수는 반드시 2개 이하
    int activeCount = queueCachePort.getUserActiveBoothCount(userId);
    assertThat(activeCount).isLessThanOrEqualTo(2);
}
```

---

**Last Updated**: 2025-12-17
**Version**: 1.0