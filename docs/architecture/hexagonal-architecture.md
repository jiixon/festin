# Hexagonal Architecture 구현 가이드

## 🏛️ 계층 구조

```
External World (HTTP, DB, MQ, FCM)
        ↓
Adapter (Infrastructure 구현)
        ↓
Port (Interface 계약)
        ↓
Application (UseCase - 흐름 조율)
        ↓
Domain (Core - 비즈니스 로직)
```

**핵심**: 의존성 방향은 **외부 → 내부** (단방향)

---

## 📐 계층별 책임 상세

### 1️⃣ Domain (핵심 계층)

**역할**: 비즈니스 규칙 구현

**구성요소**:
- `model/`: 도메인 엔티티 (Waiting, Booth, User 등)
- `policy/`: 비즈니스 정책 객체 (MaxWaitingPolicy, BoothCapacityPolicy 등)
- `exception/`: 도메인 예외 (AlreadyRegisteredException 등)

**원칙**:
- ✅ Infrastructure 완전 독립
- ✅ 순수 Java/POJO만 사용
- ✅ 불변 규칙(Invariants) 보장
- ❌ 외부 라이브러리 의존 금지 (JPA, Redis 등)
- ❌ Port/Adapter 알지 못함

**예시**:
```java
// ✅ 올바른 Domain 코드
public class Waiting {
    private WaitingId id;
    private UserId userId;
    private BoothId boothId;
    private WaitingStatus status;

    public void enter() {
        if (status != WaitingStatus.CALLED) {
            throw new InvalidStatusException("호출된 상태가 아닙니다.");
        }
        this.status = WaitingStatus.ENTERED;
        this.enteredAt = LocalDateTime.now();
    }
}

// ❌ 잘못된 Domain 코드
@Entity  // ❌ JPA 의존
public class Waiting {
    @Id
    private Long id;

    public void enter() {
        repository.save(this);  // ❌ Infrastructure 직접 의존
    }
}
```

---

### 2️⃣ Application (UseCase 계층)

**역할**: 비즈니스 흐름 조율

**구성요소**:
- `port/in/`: Inbound Port (외부 → 내부 진입점)
- `port/out/`: Outbound Port (내부 → 외부 요청)
- `service/`: UseCase 구현체

**원칙**:
- ✅ Port(Interface)만 의존
- ✅ Domain Policy 실행
- ✅ 트랜잭션 경계 설정
- ❌ Infrastructure 직접 의존 금지
- ❌ 비즈니스 규칙 직접 구현 금지

**예시**:
```java
// ✅ 올바른 Application 코드
@Service
@Transactional
public class EnqueueService implements EnqueueUseCase {

    private final WaitingQueuePort queuePort;  // Port 의존
    private final BoothPort boothPort;
    private final MaxWaitingPolicy maxWaitingPolicy;  // Domain Policy

    @Override
    public EnqueueResult execute(EnqueueCommand command) {
        // 1. Policy 검증
        maxWaitingPolicy.validate(command.getUserId());

        // 2. Domain 객체 조회
        Booth booth = boothPort.findById(command.getBoothId());

        // 3. Port를 통한 외부 시스템 호출
        queuePort.enqueue(command.getUserId(), command.getBoothId());

        return EnqueueResult.of(...);
    }
}

// ❌ 잘못된 Application 코드
@Service
public class EnqueueService {

    private final RedisTemplate redisTemplate;  // ❌ Infrastructure 직접 의존

    public void execute(EnqueueCommand command) {
        // ❌ 비즈니스 규칙을 Service에서 직접 구현
        if (activeBoothCount >= 2) {
            throw new MaxWaitingExceededException();
        }

        redisTemplate.opsForZSet().add(...);  // ❌ Infrastructure 직접 사용
    }
}
```

---

### 3️⃣ Port (Interface 계층)

**역할**: Application ↔ Infrastructure 간 계약

**분류**:
- **Inbound Port**: UseCase 인터페이스 (외부 → 내부)
- **Outbound Port**: Repository, Cache 인터페이스 (내부 → 외부)

**원칙**:
- ✅ 도메인 용어 사용
- ✅ 기술 중립적
- ✅ Application 계층에 위치
- ❌ 구현 힌트 포함 금지

**예시**:
```java
// ✅ 올바른 Port 정의
public interface WaitingQueuePort {
    void enqueue(UserId userId, BoothId boothId);
    Optional<UserId> dequeue(BoothId boothId);
    int getPosition(UserId userId, BoothId boothId);
}

// ❌ 잘못된 Port 정의
public interface WaitingQueuePort {
    void addToRedisZSet(String key, String value);  // ❌ 기술 노출
    Optional<String> popFromQueue(String queueName);
}
```

---

### 4️⃣ Adapter (Infrastructure 계층)

**역할**: Port 구현 및 외부 시스템 통신

**분류**:
- **Inbound Adapter**: Controller, EventListener
- **Outbound Adapter**: Repository, Cache, MessageQueue

**원칙**:
- ✅ Port 구현
- ✅ Infrastructure 세부사항 캡슐화
- ✅ 기술 스택 변경 시 Adapter만 수정
- ❌ 비즈니스 로직 포함 금지

**예시**:
```java
// ✅ 올바른 Inbound Adapter (Controller)
@RestController
@RequestMapping("/api/v1/waitings")
public class WaitingController {

    private final EnqueueUseCase enqueueUseCase;  // Port 의존

    @PostMapping
    public ResponseEntity<EnqueueResponse> enqueue(
        @RequestBody EnqueueRequest request
    ) {
        // DTO → Command 변환만
        EnqueueCommand command = EnqueueCommand.of(
            UserId.of(request.getUserId()),
            BoothId.of(request.getBoothId())
        );

        EnqueueResult result = enqueueUseCase.execute(command);

        // Result → DTO 변환만
        return ResponseEntity.ok(EnqueueResponse.from(result));
    }
}

// ✅ 올바른 Outbound Adapter (Cache)
@Component
public class RedisCacheAdapter implements WaitingQueuePort {

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public void enqueue(UserId userId, BoothId boothId) {
        String key = "queue:booth:" + boothId.getValue();
        double score = System.currentTimeMillis();
        redisTemplate.opsForZSet().add(key, userId.getValue().toString(), score);
    }

    @Override
    public int getPosition(UserId userId, BoothId boothId) {
        String key = "queue:booth:" + boothId.getValue();
        Long rank = redisTemplate.opsForZSet().rank(key, userId.getValue().toString());
        return rank != null ? rank.intValue() + 1 : -1;
    }
}
```

---

## 🔄 데이터 흐름

```
Request (DTO)
    ↓
Controller (Adapter In) - DTO → Command 변환
    ↓
UseCase (Port In) - 흐름 조율
    ↓
Domain Policy - 비즈니스 규칙 검증
    ↓
Repository/Cache (Port Out) - 데이터 저장/조회
    ↓
Adapter Out - Infrastructure 구현
    ↓
Response (DTO)
```

**핵심 원칙**:
1. **Controller는 변환만**: DTO ↔ Command/Result
2. **UseCase는 조율만**: Port를 통한 흐름 제어
3. **Domain은 로직만**: 순수 비즈니스 규칙
4. **Adapter는 구현만**: 기술 세부사항

---

## 🛡️ 격리 원칙

### Domain 격리
- 프레임워크, 라이브러리로부터 완전 격리
- 비즈니스 규칙 변경 시 Domain만 수정
- 기술 스택 변경 시 Domain 영향 없음

### Application 격리
- Infrastructure 구현 방법 모름
- Port를 통해서만 외부 시스템 접근
- 구현체 교체 가능 (테스트 용이)

### Infrastructure 격리
- Domain, Application 로직 포함 금지
- 순수 기술 구현만
- 교체 가능하도록 설계

---

## 🚫 금지 패턴

### ❌ 1. 계층 건너뛰기
```java
// ❌ Controller가 Domain 직접 접근
@RestController
public class WaitingController {
    private final WaitingRepository repository;  // ❌

    @GetMapping
    public Waiting getWaiting(Long id) {
        return repository.findById(id);  // ❌ UseCase 건너뜀
    }
}
```

### ❌ 2. 역방향 의존성
```java
// ❌ Domain이 Application 의존
public class Waiting {
    private final WaitingService service;  // ❌

    public void complete() {
        service.sendNotification(this);  // ❌
    }
}
```

### ❌ 3. Entity 직접 노출
```java
// ❌ Controller에서 Entity 반환
@GetMapping
public WaitingEntity getWaiting(Long id) {  // ❌
    return repository.findById(id);
}
```

### ❌ 4. Infrastructure 노출
```java
// ❌ Port에서 기술 스택 언급
public interface WaitingPort {
    void saveToMongoDB(Waiting waiting);  // ❌
    Optional<Waiting> findFromRedis(Long id);  // ❌
}
```

---

## ✅ 올바른 구조 예시

### 대기 등록 흐름
```java
// 1. Inbound Adapter (Controller)
@PostMapping("/waitings")
public EnqueueResponse enqueue(@RequestBody EnqueueRequest request) {
    EnqueueCommand command = toCommand(request);
    EnqueueResult result = enqueueUseCase.execute(command);
    return toResponse(result);
}

// 2. Inbound Port (UseCase Interface)
public interface EnqueueUseCase {
    EnqueueResult execute(EnqueueCommand command);
}

// 3. Application Service (UseCase 구현)
@Service
public class EnqueueService implements EnqueueUseCase {
    public EnqueueResult execute(EnqueueCommand command) {
        maxWaitingPolicy.validate(command.getUserId());
        queuePort.enqueue(command.getUserId(), command.getBoothId());
        return EnqueueResult.of(...);
    }
}

// 4. Outbound Port (Repository Interface)
public interface WaitingQueuePort {
    void enqueue(UserId userId, BoothId boothId);
}

// 5. Outbound Adapter (Redis 구현)
@Component
public class RedisCacheAdapter implements WaitingQueuePort {
    public void enqueue(UserId userId, BoothId boothId) {
        redisTemplate.opsForZSet().add(...);
    }
}
```

---

## 💡 구현 가이드

### 새 기능 추가 순서
1. **Domain 정의**: 비즈니스 규칙 작성
2. **Port 정의**: Inbound/Outbound Interface 작성
3. **Application 구현**: UseCase 흐름 조율
4. **Adapter 구현**: Controller, Repository 등
5. **테스트 작성**: 각 계층별 독립 테스트

### 패키지 네이밍
```
com.festin.app.waiting/
├── adapter/
│   ├── in/web/          # WaitingController
│   └── out/cache/       # RedisCacheAdapter
├── application/
│   ├── port/in/         # EnqueueUseCase
│   ├── port/out/        # WaitingQueuePort
│   └── service/         # EnqueueService
└── domain/
    ├── model/           # Waiting
    ├── policy/          # MaxWaitingPolicy
    └── exception/       # WaitingException
```

---

**Last Updated**: 2025-12-17
**Version**: 1.0