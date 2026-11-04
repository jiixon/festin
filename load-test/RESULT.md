# Festin 부하 테스트 결과

> **이 문서는 1차(최적화 전) 결과입니다.** 아래 "병목 원인 분석"은 당시의 추정이며,
> 실제 원인은 Redis만 쓰는 API의 불필요한 `@Transactional`로 확인되었습니다.
> 최적화 후 결과는 맨 아래 [개선 후 (2026-01-21)](#개선-후-2026-01-21) 와
> [`load-test-report.md`](load-test-report.md) 를 참고하세요.

**테스트 일시**: 2026-01-17
**테스트 도구**: k6
**테스트 환경**: AWS (t3.micro + db.t4g.micro)

---

## 테스트 환경

| 구성요소 | 스펙 |
|---------|------|
| EC2 | t3.micro (2 vCPU, 1GB RAM) |
| MySQL | RDS db.t4g.micro |
| Redis | Docker redis:7.0-alpine |
| RabbitMQ | Docker rabbitmq:3-management-alpine |

---

## 1. 기본 부하 테스트 (enqueue-test.js)

### 시나리오
- VUser: 10 → 50 → 100 → 200 (점진적 증가)
- API: 대기등록 + 순번조회

### 결과

| 지표 | 목표 | 결과 | 상태 |
|-----|-----|-----|------|
| p(95) Latency | < 2000ms | **86ms** | ✅ |
| Error Rate | < 10% | **0%** | ✅ |

| 항목 | 값 |
|-----|-----|
| 최대 VUser | 200명 |
| TPS | 78 req/s |
| 성공률 | 100% |
| 대기등록 p(95) | 103ms |
| 순번조회 p(95) | 77ms |

**결론**: VUser 200명까지 안정적 처리

---

## 2. 스파이크 테스트 (spike-test.js)

### 시나리오
- 순간 폭주: 500 req/s로 급증
- 최대 VUser: 1000명

### 결과

| 지표 | 목표 | 결과 | 상태 |
|-----|-----|-----|------|
| p(99) Latency | < 5000ms | **14,050ms** | ❌ |
| Error Rate | < 20% | **0%** | ✅ |

| 항목 | 값 |
|-----|-----|
| 최대 VUser | 1000명 |
| TPS | 138 req/s |
| 성공률 | 100% |
| avg Latency | 622ms |
| med Latency | 15ms |
| p(99) Latency | 14초 |
| 드롭된 요청 | 5,883건 |

**결론**: 서버는 버티지만 응답 지연 심각 (Tail Latency)

---

## 병목 원인 분석

| 원인 | 가능성 |
|-----|-------|
| t3.micro CPU 크레딧 소진 | 높음 |
| DB Connection Pool 부족 (기본 10개) | 높음 |
| JVM GC Pause | 중간 |

---

## 개선 권장사항

### 단기 (비용 없음)
- DB Connection Pool: 10 → 30
- JVM: `-Xmx512m -XX:+UseG1GC`

### 중기 (비용 발생)
- EC2: t3.micro → t3.small
- RDS: db.t4g.micro → db.t4g.small

---

## 요약

| VUser | 상태 |
|-------|------|
| 200명 | ✅ 안정 |
| 1000명 | ⚠️ 지연 발생 (서버는 생존) |

---

## 개선 후 (2026-01-21)

### 실제 원인
Prometheus 의 `hikaricp_connections_pending` 급증을 추적한 결과, Redis 만 호출하는
`EnqueueService` / `GetPositionService` 가 `@Transactional` 때문에 DB 커넥션을 점유하고 있었다.
커넥션 획득 대기가 tail latency(p99 14초, max 19초)의 원인.

### 수정
| 커밋 | 내용 |
|------|------|
| `57ff031` | Enqueue/GetPosition 의 불필요한 `@Transactional` 제거 |
| `4ee9c92` | DB Connection Pool 10 → 30 |
| `fe7891b` | HikariCP 메트릭(active/idle/pending/acquire) 추가 — 재발 감지 |

> 풀 증가도 함께 적용했다. 다만 애너테이션 제거 후 두 API 는 DB 커넥션을 사용하지 않으므로,
> 해당 경로의 지연에는 풀 크기가 관여하지 않는다.

### 스파이크 테스트 (동일 스크립트 · 동일 인스턴스, 500 req/s)
| 지표 | 최적화 전 | 최적화 후 | 개선율 |
|------|----------|----------|--------|
| p(95) | 1,160ms | 363ms | 69% |
| **p(99)** | **14,050ms** | **558ms** | **96%** |
| max | 19,250ms | 1,780ms | 91% |
| 에러율 | 0% | 0% | 유지 |
| 최대 VUser | 1,000 | 500 | — |

추가 인프라 비용 $0 (t3.micro 그대로). 상세: [`load-test-report.md`](load-test-report.md)