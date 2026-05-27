# Festin 관측 (Observability) — 분리형 구성

t3.micro(RAM 1GB)의 메모리를 보호하고 측정 오염(관찰자 효과)을 피하기 위해,
**수집(EC2)** 과 **저장·시각화(로컬 맥)** 를 분리한다.

```
┌─ EC2 (t3.micro, 911MiB) ─────────────┐        ┌─ 맥 (로컬) ───────────────┐
│  festin-app    :8080/actuator/prom   │        │  Prometheus (30d 보존)    │
│  festin-redis                        │        │  Grafana    :3001         │
│  festin-rabbitmq :15692 (플러그인 ON) │◀──SSH──│  (host.docker.internal)   │
│  + node-exporter   :9100  (~20MiB)   │ tunnel └───────────────────────────┘
│  + redis-exporter  :9121  (~10MiB)   │
└──────────────────────────────────────┘  EC2 추가 점유 ~30MiB
```

메트릭 포트는 전부 `127.0.0.1` 바인딩 → **외부 비노출, SSH 터널로만 접근** (보안그룹 수정 불필요).

---

## 1. EC2 — exporter 기동

```bash
# (최초 1회) RabbitMQ 메트릭 포트(127.0.0.1:15692) 반영 위해 rabbitmq 재생성
#  ※ 데이터는 rabbitmq-data 볼륨에 보존됨. 진행 중 메시지/연결은 잠깐 끊김.
docker compose -f docker-compose.prod.yml up -d rabbitmq

# 엣지 exporter 기동
docker compose -f docker-compose.exporters.yml up -d

# 확인
curl -s -o /dev/null -w "node    %{http_code}\n" http://localhost:9100/metrics
curl -s -o /dev/null -w "redis   %{http_code}\n" http://localhost:9121/metrics
curl -s -o /dev/null -w "rabbit  %{http_code}\n" http://localhost:15692/metrics
curl -s -o /dev/null -w "app     %{http_code}\n" http://localhost:8080/actuator/prometheus
```

## 2. 맥 — SSH 터널 + viewer 기동

```bash
# (1) 터널 — viewer 사용하는 동안 이 세션 유지
ssh -i <key.pem> -N \
  -L 18080:localhost:8080 \
  -L 9100:localhost:9100 \
  -L 9121:localhost:9121 \
  -L 15692:localhost:15692 \
  ubuntu@<EC2_HOST>

# (2) 다른 터미널에서 Prometheus + Grafana
docker compose -f docker-compose.viewer.yml up -d
```

- Grafana: http://localhost:3001  (admin / festin123) → **Festin Performance Dashboard**
- Prometheus: http://localhost:9090 → Status > Targets 에서 4개 job(festin-app/node/redis/rabbitmq) 모두 **UP** 확인

## 3. 대시보드 패널

| 그룹 | 패널 | 감시 대상 |
|------|------|-----------|
| 기존 | API p95/p99, Error Rate, TPS, HikariCP(active/pending/acquire/timeout), JVM Heap·Threads, CPU | 동시성·부하 |
| Redis | Memory, Keys | key 미정리로 인한 무한 증가 |
| RabbitMQ | Backlog(ready/unacked), Throughput | 컨슈머 지연·큐 적체 |
| Host | Memory(1GB), Disk, CPU | off-heap 누수·OOM·로그 디스크 |

> **CPU 크레딧**(t3.micro 버스터블)은 Prometheus로 안 잡힘 → AWS **CloudWatch `CPUCreditBalance`** 콘솔에서 확인. 지속 부하 시 크레딧 소진 → CPU%는 낮은데 p95만 튀는 현상 주의.

## 4. Phase 4b(무인 tail) 관측

맥이 꺼지면 로컬 Prometheus도 멈춘다. 1~2주 무인 tail 구간은 **CloudWatch**(호스트 메모리/CPU/크레딧/디스크)로 감시하고, 깊은 진단은 Phase 4a(풀 Prometheus)에서 끝낸다.

## 5. 종료

```bash
# 맥
docker compose -f docker-compose.viewer.yml down      # -v 붙이면 데이터까지 삭제
# 터널 세션 종료(Ctrl+C)

# EC2
docker compose -f docker-compose.exporters.yml down
```

> 참고: 전부 한 박스(EC2)에서 돌리는 올인원 구성은 `docker-compose.monitoring.yml` 에 남아있으나,
> t3.micro 메모리 제약상 위 분리형(exporters + viewer)을 권장한다.