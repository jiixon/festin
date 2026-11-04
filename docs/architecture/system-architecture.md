# System Architecture

## 🏗️ 시스템 구성도

```
┌─────────────────┐
│     Client      │
└────────┬────────┘
         │ HTTP/REST
         ↓
┌─────────────────┐
│  Spring Boot    │
│   Application   │
└──┬─────┬─────┬──┘
   │     │     │ 호출 알림 발행
   ↓     ↓     ↓
┌─────┐┌─────┐┌──────────┐
│MySQL││Redis││ RabbitMQ │
└─────┘└─────┘└────┬─────┘
                   │ 소비 (FcmNotificationConsumer)
                   ↓
             ┌───────────┐
             │ FCM (푸시) │
             └───────────┘
```

---

## 🗄️ 데이터 저장소 역할 분리

### MySQL (영구 저장소)
**역할**: 호출 이후 이력 영구 보관

**저장 데이터**:
- `University`: 대학 정보
- `User`: 사용자 정보
- `Booth`: 부스 정보
- `MenuItem`: 메뉴 정보
- **`Waiting`**: 호출 이후 대기 이력 (CALLED, ENTERED, COMPLETED)

**특징**:
- **호출 전에는 저장 안 함** (Redis만 존재)
- 호출 시점부터 영구 기록 시작
- 통계, 분석, 감사 추적에 사용

---

### Redis (캐시 & 대기열)
**역할**: 실시간 대기열 관리 및 캐싱

**주요 용도**:
- 대기열 관리 (Sorted Set)
- 활성 부스 목록 (Set)
- 부스 현재 인원 (String, INCR/DECR)
- 부스 메타 캐싱 (Hash)
- FCM 토큰 캐싱 (String)
- 분산 락 (String)

**상세 데이터 구조**: [database/redis.md](../database/redis.md) 참고

---

## 🔄 데이터 흐름

### 1️⃣ 대기 등록 (Redis만)
```
Client → Spring Boot
  ↓
1. 활성 부스 개수 확인 (최대 2개)
2. 대기열 중복 체크
3. ZADD로 대기열 추가
4. SADD로 활성 부스 목록 추가
  ↓
Client ← 201 Created
```

**특징**: MySQL 접근 없음 (빠른 응답)

---

### 2️⃣ 다음 사람 호출 (MySQL + Redis)
```
Staff → Spring Boot
  ↓
1. Redis: 부스 정원 확인
2. Redis: ZPOPMIN으로 대기열에서 제거
3. MySQL: 사용자 존재 확인 (없으면 폐기하고 다음 대기자로)
4. Redis: Soft Lock 생성
5. Redis: SREM으로 활성 부스 목록에서 제거
6. MySQL: Waiting 테이블에 INSERT (영구 기록 시작)
7. Redis: Soft Lock 삭제
8. RabbitMQ: 호출 알림 발행
  ↓
Staff ← 200 OK (waitingId 반환)

(응답과 별개로) RabbitMQ → FcmNotificationConsumer → FCM 푸시
```

**핵심**: 이 시점부터 MySQL에 기록 시작

---

### 3️⃣ 입장 확인 (MySQL + Redis)
```
Staff → Spring Boot
  ↓
1. MySQL: Waiting 상태 확인 (CALLED?)
2. MySQL: UPDATE status = ENTERED
3. Redis: INCR booth:{boothId}:current (정원 +1)
  ↓
Staff ← 200 OK
```

---

### 4️⃣ 체험 완료 (MySQL + Redis)
```
Staff → Spring Boot
  ↓
1. MySQL: Waiting 상태 확인 (ENTERED?)
2. MySQL: UPDATE status = COMPLETED
3. Redis: DECR booth:{boothId}:current (정원 -1)
  ↓
Staff ← 200 OK
```

---

## ⚡ 동기/비동기 처리

### 동기 처리 (Sync)
- **대기 등록/조회/취소**: Redis 작업 완료 후 즉시 응답
- **호출/입장/완료**: MySQL + Redis 트랜잭션 완료 후 응답

### 비동기 처리 (Async)
- **호출 알림**: 호출 처리 후 RabbitMQ에 발행하고, Consumer가 FCM으로 발송
- **실패 처리**: 현재 실패한 알림은 재발송되지 않음 (자세한 내용은 아래 "외부 시스템 연동")

### 배치 처리 (Scheduled)
- **노쇼 처리**: 주기적으로 타임아웃 체크 (1분마다)
  - CALLED 상태에서 5분 경과 시 NO_SHOW 처리

---

## 🔒 트랜잭션 전략

### MySQL 트랜잭션
```java
@Transactional  // MySQL 트랜잭션
public void callNext(BoothId boothId) {
    // 1. Redis에서 dequeue
    UserId userId = queuePort.dequeue(boothId);

    // 2. MySQL에 INSERT (트랜잭션 보장)
    Waiting waiting = Waiting.createCalled(userId, boothId);
    waitingRepository.save(waiting);

    // 3. RabbitMQ에 호출 알림 발행 (발송은 Consumer가 처리)
}
```

**주의**: Redis는 트랜잭션 범위 밖 → 분산 정합성 고려 필요
**상세**: [design-decisions/distributed-consistency.md](../design-decisions/distributed-consistency.md) 참고

---

## 🌐 외부 시스템 연동

### RabbitMQ
**역할**: 호출 처리와 푸시 발송 분리

**구성**:
- 큐: `booth-call-notifications` (durable)
- 발행: `RabbitMqNotificationAdapter`
- 소비: `FcmNotificationConsumer` (수동 ACK, 이벤트 ID로 중복 처리 방지)
- 위치: Docker Compose의 `rabbitmq` 서비스

**특징**:
- 발행 실패는 로그만 남기고 호출 응답은 정상 반환 (재발행 없음)

### FCM (Firebase Cloud Messaging)
**역할**: 푸시 알림 발송

**타이밍**:
- **호출 시**: 부스 입장 순서 알림
- **노쇼 처리 시**: (향후) "노쇼 처리되었습니다" 알림

**특징**:
- RabbitMQ Consumer가 발송
- FCM 오류는 재시도하지 않고 ACK (유효하지 않은 토큰은 캐시에서 삭제)
- 그 외 처리 오류는 NACK으로 큐에 되돌리지만, 이벤트 ID가 `PROCESSING`으로 남아 있어 재전달 시 중복으로 판단하고 ACK함 (현재 재발송되지 않음)
- 알림 실패해도 대기 흐름은 정상 진행

---

## 📊 성능 고려사항

### Redis 성능
- **대기열 조회**: O(log N) - Sorted Set ZRANK
- **대기 등록**: O(log N) - Sorted Set ZADD
- **정원 확인**: O(1) - String GET

### MySQL 인덱싱
- **호출 이력 조회**: `(user_id, booth_id, status)` 복합 인덱스
- **노쇼 배치**: `(status, called_at)` 인덱스

### 캐싱 전략
- **부스 메타 정보**: Redis Hash 캐싱 (TTL 10분)
- **FCM 토큰**: Redis String 캐싱 (TTL 60일)

---

## 🚨 장애 시나리오

### Redis 장애
- **대기 등록/조회 불가**: 503 Service Unavailable 반환
- **복구 시**: MySQL 이력 기반 Redis 재구성 필요

### MySQL 장애
- **대기 등록은 가능** (Redis만 사용)
- **호출은 불가** (MySQL 기록 필수)

### FCM 장애
- **대기 흐름은 정상**: 알림만 실패
- **재시도 없음**: FCM 오류가 난 메시지는 ACK 처리되어 다시 발송되지 않음

### RabbitMQ 장애
- **호출은 정상**: 발행 실패는 로그만 남김
- **해당 알림은 유실**: 재발행 로직이 없음

---

**Last Updated**: 2026-11-05
**Version**: 1.0