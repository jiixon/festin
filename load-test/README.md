# Festin 성능 테스트 가이드

## 📋 테스트 환경 스펙

| 구성요소 | 스펙 |
|---------|------|
| EC2 | t3.micro (2 vCPU, 1GB RAM) |
| MySQL | RDS db.t4g.micro (2 vCPU, 1GB RAM) |
| Redis | Docker redis:7.0-alpine |
| RabbitMQ | Docker rabbitmq:3-management-alpine |

## 🚀 Quick Start

### 1. k6 설치

```bash
# macOS
brew install k6

# Linux
sudo gpg -k
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update
sudo apt-get install k6
```

### 2. 모니터링 스택 실행

```bash
# 프로젝트 루트에서 실행
docker compose -f docker-compose.prod.yml -f docker-compose.monitoring.yml up -d
```

### 3. 대시보드 접속

- **Grafana**: http://localhost:3001 (admin / festin123)
- **Prometheus**: http://localhost:9090

### 4. 테스트 실행

```bash
# 기본 부하 테스트 (대기등록 + 순번조회)
k6 run load-test/enqueue-test.js \
  -e BASE_URL=http://your-server:8080 \
  -e BOOTH_ID=1 \
  -e AUTH_TOKEN=your-jwt-token

# 스파이크 테스트 (축제 오픈 시뮬레이션)
k6 run load-test/spike-test.js \
  -e BASE_URL=http://your-server:8080 \
  -e BOOTH_ID=1 \
  -e AUTH_TOKEN=your-jwt-token
```

## 📊 테스트 시나리오

### enqueue-test.js (기본 부하)
| Stage | Duration | VUsers | 목적 |
|-------|----------|--------|-----|
| 1 | 30s | 10 | Warm-up |
| 2 | 1m | 50 | Ramp-up |
| 3 | 2m | 100 | 정상 부하 |
| 4 | 1m | 200 | 스트레스 |
| 5 | 30s | 0 | Cool-down |

### spike-test.js (스파이크)
| Stage | Duration | Rate | 목적 |
|-------|----------|------|-----|
| 1 | 10s | 10/s | 천천히 시작 |
| 2 | 5s | 500/s | 급격히 증가 |
| 3 | 30s | 500/s | 고부하 유지 |
| 4 | 10s | 10/s | 감소 |

## 📈 관찰 포인트

### Grafana 대시보드에서 확인할 것

1. **API Latency (p95, p99)**
   - 언제부터 응답 시간이 급격히 증가하는가?
   - 어떤 API가 가장 느린가?

2. **TPS (Requests/sec)**
   - 최대 처리량은 얼마인가?
   - TPS가 떨어지기 시작하는 시점은?

3. **Error Rate**
   - 에러가 발생하기 시작하는 VUser 수는?
   - 어떤 에러 코드가 가장 많은가?

4. **CPU Usage**
   - CPU가 병목인가? (90% 이상 지속 시)

5. **DB Connection Pool**
   - Pending이 증가하면 DB Connection 부족
   - Active가 Max에 도달하면 Connection Pool 증설 필요

6. **JVM Heap Memory**
   - 메모리 누수가 있는가?
   - GC 빈도가 증가하는가?

## 🔍 결과 분석 템플릿

```markdown
## 테스트 결과 요약

**테스트 일시**: YYYY-MM-DD HH:MM
**테스트 시나리오**: enqueue-test / spike-test

### 주요 지표

| 지표 | VU 10 | VU 50 | VU 100 | VU 200 |
|-----|-------|-------|--------|--------|
| TPS | | | | |
| p95 Latency | | | | |
| p99 Latency | | | | |
| Error Rate | | | | |
| CPU Usage | | | | |

### 병목 분석

- **병목 지점**: (CPU / DB Connection / Redis / Network)
- **임계점**: VUser OOO명 시점에서 성능 저하 시작
- **증상**: (응답 지연 / 에러 급증 / Timeout)

### 개선 방안

1.
2.
3.
```

## ⚠️ 주의사항

1. **t3.micro는 버스트 크레딧 기반**
   - 장시간 테스트 시 크레딧 소진으로 성능 급락 가능
   - CPU 크레딧 잔량 모니터링 필요

2. **테스트용 JWT 토큰 필요**
   - 실제 카카오 로그인 토큰 또는 테스트용 토큰 발급 필요
   - 토큰 만료 시간 고려

3. **테스트 데이터 정리**
   - 테스트 후 Redis 대기열 데이터 정리 필요
   - `redis-cli FLUSHDB` 또는 특정 키 삭제

4. **비용 주의**
   - RDS는 I/O 기반 과금 가능
   - 대량 테스트 시 비용 발생 가능