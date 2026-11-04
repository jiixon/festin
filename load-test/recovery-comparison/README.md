# Issue #4 실험용 코드 (Experimental Code)

## 📌 목적

이 디렉토리는 **Issue #4: MySQL-Redis 정합성 보장** 문제 해결을 위해 작성한 실험용 코드를 보관합니다.

TransactionalEventListener vs Soft Lock 두 가지 접근법을 **정량적으로 측정**하여 최종 결정의 근거로 사용했습니다.

---

## 📂 파일 구조

### 1. CallNextServiceV2EventListener.java
**TransactionalEventListener 방식 구현**

**핵심 로직:**
```
1. Peek (제거하지 않음)
2. MySQL save (@Transactional)
3. AFTER_COMMIT 이벤트 발행
4. TransactionalEventListener에서 Redis dequeue
```

**문제점:**
- AFTER_COMMIT도 실패 가능 → 결국 배치 필요
- Peek-Save gap에 Race Condition 가능
- 역할 전도 (MySQL 먼저 → Redis 나중)

---

### 2. CallNextServiceV3SoftLock.java
**Soft Lock 방식 구현** (✅ 최종 선택)

**핵심 로직:**
```
1. Redis dequeue (즉시 제거)
2. Soft Lock 생성 (temp:calling:{boothId}:{userId})
3. MySQL save (@Transactional)
4. 성공: Soft Lock 삭제
5. 실패: Soft Lock 남김 (배치가 롤백)
```

**장점:**
- 자연스러운 흐름 (Redis → MySQL)
- timestamp 보존으로 정확한 복구
- 배치 효율성: O(실패 건수)

**최종 적용:** `CallNextService.java`에 통합됨

---

### 3. EventListenerRecoveryBatch.java
**TransactionalEventListener 방식 배치 보정**

**로직:**
- MySQL에서 최근 5분 CALLED 전체 조회
- 각 항목마다 Redis 확인
- Redis에 여전히 있으면 제거

**비효율:**
- O(최근 CALLED 수) - 100건 조회 시 64ms

---

### 4. CallNextEvent.java
**TransactionalEventListener용 이벤트 클래스**

V2 방식에서만 사용.

---

## 📊 측정 결과

상세한 측정 결과는 [`RESULT.md`](RESULT.md) 참고.

| 평가 항목 | TransactionalEventListener | Soft Lock | 승자 |
|-----------|---------------------------|-----------|------|
| **배치 스캔 효율** | 64ms (100건 조회) | 5ms (1건만) | **Soft Lock** (12.8배 빠름) |
| **복구 정확성** | Peek이라 복구 불필요 | timestamp 보존 | **Soft Lock** |
| **아키텍처 단순성** | 역할 전도, Peek gap | 자연스러운 흐름 | **Soft Lock** |

---

## ✅ 최종 결정

**Soft Lock 방식 채택**

이유:
1. 배치 효율성: 12.8배 빠름 (5ms vs 64ms)
2. 복구 정확성: timestamp 보존으로 정확한 위치 복구
3. 아키텍처 일관성: Redis → MySQL 흐름 유지
4. 명시적 마킹: Soft Lock으로 실패 지점 명확히 표시

---

## 🔗 관련 문서

- [`RESULT.md`](RESULT.md): 측정 결과 종합
- [`/docs/design-decisions/distributed-consistency.md`](../../docs/design-decisions/distributed-consistency.md): 설계 결정 (Outbox 기각 사유 포함)

---

**작성일:** 2026-01-03
**최종 업데이트:** 2026-01-03