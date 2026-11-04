# 런북 — RDS → EC2 컨테이너 MySQL 이관 (데모 유지, RDS 비용 0)

> 목표: `www.festin.shop` 데모는 살린 채 **RDS를 삭제**해 월 비용을 없앤다.
> 구조 변화: `EC2 + (Redis·RabbitMQ 컨테이너) + 외부 RDS` → `EC2 + (MySQL·Redis·RabbitMQ 컨테이너)`.
> app 은 `DB_HOST` 환경변수만 바라보므로 **애플리케이션 코드 변경 없음**. 코드/컴포즈 변경은 이미 반영됨(`docker-compose.prod.yml`, `.github/workflows/cd.yml`).

---

## 0. 준비물 · 사전 확인

- EC2 SSH 접속 (CD가 쓰는 `EC2_HOST`/`EC2_SSH_KEY` 동일 계정)
- 현재 RDS 접속 정보: **엔드포인트, 포트(3306), DB_NAME, DB_USER, DB_PASSWORD**
- EC2 `~/festin/.env` 파일 (docker-compose 가 자동 로드하는 환경변수. `DB_HOST`, `DB_USER`, `DB_PASSWORD`, `DB_NAME` 등이 여기 있음)
- ⚠️ `DB_USER` 가 `root` 가 아니어야 함(컨테이너 MySQL 의 `MYSQL_USER` 는 root 지정 불가). 보통 `festin` 같은 별도 유저 — 맞는지 확인.

전 과정 예상 소요: 20~30분. 실제 삭제(7단계)는 며칠 관찰 후.

---

## 1. EC2 RAM 확보 — 스왑 2GB (OOM 예방)

t3.micro(1GB) 에 MySQL 을 추가하므로 스왑을 먼저 잡는다. 저트래픽 데모엔 충분.

```bash
# EC2 안에서
free -h                                   # 현재 메모리/스왑 확인
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab   # 재부팅 후에도 유지
free -h                                   # Swap: 2.0Gi 확인
```

---

## 2. 최종 스냅샷 백업 (되돌릴 수 있게)

**AWS 콘솔** → RDS → 해당 인스턴스 → *Actions → Take snapshot* → 이름 `festin-final-YYYYMMDD`.
→ 이 스냅샷이 있으면 7단계 삭제 후에도 필요 시 RDS 를 되살릴 수 있다. **반드시 먼저.**

---

## 3. 코드/컴포즈 최신화 + .env 에 root 패스워드 추가

```bash
# EC2 안에서
cd ~/festin
git pull                                  # mysql 서비스가 추가된 docker-compose.prod.yml 반영
```

`~/festin/.env` 에 한 줄 추가(컨테이너 MySQL root 비번, 아무 강한 값):

```dotenv
DB_ROOT_PASSWORD=<강한-랜덤-문자열>
```

`.env` 의 `DB_NAME`, `DB_USER`, `DB_PASSWORD` 는 **RDS 와 동일하게 유지**(그래야 덤프가 그대로 들어감). `DB_HOST` 는 아직 바꾸지 않는다(5단계에서 전환).

---

## 4. 컨테이너 MySQL 기동 + 헬스 확인

```bash
cd ~/festin
docker-compose -f docker-compose.prod.yml up -d mysql
docker ps | grep festin-mysql
# healthy 될 때까지 30~60초 대기 후:
docker inspect --format '{{.State.Health.Status}}' festin-mysql   # → healthy
```

---

## 5. RDS 데이터 덤프 → 컨테이너 MySQL 로 복원

```bash
# EC2 안에서. 값은 실제 RDS 정보로 치환.
RDS_HOST=<rds-엔드포인트>
DB_NAME=<db_name>
DB_USER=<db_user>

# 5-1) RDS 에서 덤프 (스키마+데이터, utf8mb4)
mysqldump -h "$RDS_HOST" -u "$DB_USER" -p \
  --single-transaction --routines --triggers --default-character-set=utf8mb4 \
  "$DB_NAME" > /tmp/festin_dump.sql
#   ↑ RDS 비밀번호 입력. 완료 후 파일 크기 확인:
ls -lh /tmp/festin_dump.sql

# 5-2) 컨테이너 MySQL 로 복원 (컨테이너 안 root 로)
docker exec -i festin-mysql \
  sh -c "exec mysql -uroot -p\"\$MYSQL_ROOT_PASSWORD\" $DB_NAME" < /tmp/festin_dump.sql

# 5-3) 복원 검증 — 테이블/행 수 눈으로 확인
docker exec -it festin-mysql \
  sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "USE '"$DB_NAME"'; SHOW TABLES; SELECT COUNT(*) FROM booth;"'
```

> 행 수가 RDS 와 맞으면 성공. `booth` 대신 실제 존재하는 테이블로 확인해도 됨.

---

## 6. app 을 컨테이너 MySQL 로 전환 + 데모 검증

`~/festin/.env` 에서 DB 접속 대상을 컨테이너로:

```dotenv
DB_HOST=mysql
DB_PORT=3306
```

app 재시작:

```bash
cd ~/festin
docker-compose -f docker-compose.prod.yml up -d --no-deps app
docker logs -f festin-app --tail 100        # 부팅 로그에서 DB 연결/Hibernate 정상 확인 (Ctrl+C 로 빠져나옴)
curl -f http://localhost:8080/api/health && echo " OK"
```

**브라우저에서 `https://www.festin.shop` 직접 확인**: 로그인 → 부스 목록 → 대기 등록 → 대기 조회가 모두 되면 이관 성공. (데이터가 그대로 보여야 함)

---

## 7. RDS 삭제 (며칠 관찰 후, 최종 단계)

6단계 검증 후 **2~3일 정상 운영 확인**하고 나서:

- **AWS 콘솔** → RDS → 인스턴스 → *Actions → Delete*
  - "최종 스냅샷 생성" 체크 (2단계에서 이미 떴어도 한 번 더 안전)
  - 자동 백업 삭제 확인
- 삭제 후 **RDS 요금 = 0**. EC2(t3.micro)만 남음.

---

## 롤백 (이관 후 문제 시)

`~/festin/.env` 의 `DB_HOST` 를 다시 RDS 엔드포인트로 되돌리고 `docker-compose ... up -d --no-deps app`.
RDS 를 이미 삭제했다면 2단계 최종 스냅샷에서 새 인스턴스로 복원 → `DB_HOST` 를 그 엔드포인트로.

---

## 참고 — cron 은 어떻게?

LLM 시뮬레이션 cron(`llm-simulation-cron.yml`)은 이 서버(`FESTIN_BASE_URL`)를 계속 때린다.
데모를 유지하면 cron 도 계속 돌아도 되지만, 부하/노이즈가 싫으면 워크플로우를 비활성(Actions 탭 → 해당 워크플로우 → `···` → Disable)하면 된다. **"139회 무인 운영" 실적은 이미 확정**이라 지금 멈춰도 이력서 근거엔 영향 없음.