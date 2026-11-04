# Festin - Coding Conventions

## 📌 개요

Festin 프로젝트의 **코딩 스타일 규칙**을 정의합니다.

---

## 🎯 네이밍 규칙

| 타입 | 패턴 | 예시 |
|------|------|------|
| **UseCase Interface** | `{동사}UseCase` | `EnqueueUseCase`, `CallNextUseCase` |
| **UseCase Implementation** | `{동사}Service` | `EnqueueService`, `CallNextService` |
| **Port Interface** | `{명사}Port` | `QueueCachePort`, `WaitingRepositoryPort` |
| **Adapter** | `{기술}{명사}Adapter` | `JpaWaitingAdapter`, `RedisQueueAdapter` |
| **Domain Entity** | `{명사}` | `Waiting`, `Booth`, `User` |
| **Policy** | `{명사}Policy` | `MaxWaitingPolicy`, `BoothCapacityPolicy` |
| **Exception** | `{원인}Exception` | `BoothFullException`, `MaxWaitingExceededException` |

### ❌ 금지 패턴
- `EnqueueUseCaseImpl` - "Impl" 접미사 금지
- `WaitingRepository` - Port는 "Port" 접미사 필수
- `WaitingRepositoryImpl` - Adapter는 구체적 기술 명시 필요

---

## 💻 코드 스타일

### 1. Dependency Injection
```java
// ✅ Constructor Injection만 사용
@RequiredArgsConstructor
@Service
public class EnqueueService implements EnqueueUseCase {
    private final QueueCachePort queueCachePort;
}

// ❌ @Autowired Field Injection 금지
```

---

### 2. DTO 패턴

- **Command/Result**: Record 사용 (불변)
- **Request/Response**: Lombok @Data 사용

---

### 3. 상수 사용
```java
// ✅ 상수로 정의
public static final int MAX_ACTIVE_BOOTHS = 2;

// ❌ 매직 넘버 금지
if (size >= 2) { }
```

---

### 4. 예외 처리
```java
// ✅ 구체적인 도메인 예외
throw new MaxWaitingExceededException("최대 2개 부스만 가능");

// ❌ 일반 Exception 금지
throw new Exception("에러");
```

---

## 📝 주석 작성

### 작성해야 할 때

**1. 비즈니스 규칙이 불명확할 때**
```java
// 멱등성: 같은 날 같은 부스 재등록 방지
String idempotencyKey = generateKey(userId, boothId, date);
```

**2. 동시성 제어 이유**
```java
// Redis 분산 락으로 동시 호출 방지
Boolean acquired = redisTemplate.opsForValue()
    .setIfAbsent(lockKey, "1", Duration.ofSeconds(10));
```

**3. Why를 설명할 때**
```java
// Kafka를 통한 알림 유실 방지
// Consumer에서 FCM 전송 실패 시 자동 재시도
kafkaProducerPort.send("booth-call-notifications", notification);
```

### ❌ 작성하지 말 것

- 코드만 봐도 명확한 경우
- What만 반복하는 주석
- 오래되어 틀린 주석

---

## 🚫 안티패턴

### 1. Entity 직접 반환
```java
// ❌ Controller에서 Entity 반환 금지
return waitingRepository.findById(id);

// ✅ DTO로 변환
return WaitingResponse.from(waiting);
```

---

### 2. Controller에 비즈니스 로직
```java
// ❌ Controller에서 Redis 직접 접근 금지
redis.opsForZSet().add(...);

// ✅ UseCase에 위임
enqueueUseCase.enqueue(command);
```

---

### 3. Domain에 Infrastructure 의존
```java
// ❌ Domain에 @Entity 금지
@Entity
public class Waiting { }

// ✅ 순수 POJO
public class Waiting {
    public void enter() { ... }
}
```

---

### 4. 긴 메서드 (30줄 이상)
```java
// ❌ 하나의 메서드가 모든 것 처리
public void processQueue() {
    // 50줄...
}

// ✅ 책임 분리 (20줄 이내)
public void processQueue() {
    validate();
    dequeue();
    notify();
}
```
---

**Last Updated**: 2025-12-08  
**Version**: 1.0