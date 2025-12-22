# CI/CD 설정 가이드

이 문서는 Festin 프로젝트의 CI/CD 파이프라인 설정 방법을 설명합니다.

## 📋 목차
1. [개요](#개요)
2. [GitHub Secrets 설정](#github-secrets-설정)
3. [EC2 서버 설정](#ec2-서버-설정)
4. [CI/CD 워크플로우](#cicd-워크플로우)
5. [배포 테스트](#배포-테스트)

---

## 개요

### CI (Continuous Integration)
- **트리거**: PR 생성/업데이트, main 브랜치 push
- **작업**:
  - Gradle 빌드
  - Testcontainers 기반 테스트 실행
  - 테스트 결과 아티팩트 업로드

### CD (Continuous Deployment)
- **트리거**: main 브랜치 push
- **작업**:
  1. Docker 이미지 빌드
  2. Docker Hub에 푸시
  3. EC2 서버에 SSH 접속
  4. 최신 이미지 pull 및 재시작

---

## GitHub Secrets 설정

GitHub 저장소의 **Settings > Secrets and variables > Actions**에서 다음 Secrets를 추가하세요.

### 1. Docker Hub 인증 정보

| Secret Name | 설명 | 예시 |
|-------------|------|------|
| `DOCKER_USERNAME` | Docker Hub 사용자명 | `your-username` |
| `DOCKER_PASSWORD` | Docker Hub 비밀번호 또는 액세스 토큰 (권장) | `dckr_pat_xxxxx` |

**Docker Hub 액세스 토큰 생성 방법**:
1. Docker Hub 로그인
2. Account Settings > Security > New Access Token
3. 토큰 이름 입력 후 생성
4. 생성된 토큰을 `DOCKER_PASSWORD`에 저장

### 2. EC2 서버 정보

| Secret Name | 설명 | 예시 |
|-------------|------|------|
| `EC2_HOST` | EC2 인스턴스 퍼블릭 IP 또는 도메인 | `1.2.3.4` 또는 `api.festin.com` |
| `EC2_USERNAME` | SSH 접속 사용자명 (보통 ec2-user 또는 ubuntu) | `ec2-user` |
| `EC2_SSH_PRIVATE_KEY` | SSH 프라이빗 키 전체 내용 | `-----BEGIN RSA PRIVATE KEY-----\n...` |

**SSH 프라이빗 키 설정**:
```bash
# 로컬에서 EC2 접속용 .pem 키 내용 복사
cat ~/.ssh/your-key.pem | pbcopy  # macOS
cat ~/.ssh/your-key.pem | xclip -selection clipboard  # Linux
```

**주의사항**:
- 키 전체 내용을 복사하세요 (`-----BEGIN` ~ `-----END` 포함)
- 개행문자가 유지되어야 합니다

---

## EC2 서버 설정

### 1. 프로젝트 디렉토리 생성

```bash
# EC2 서버에 SSH 접속
ssh -i your-key.pem ec2-user@your-ec2-ip

# 홈 디렉토리에 프로젝트 폴더 생성
mkdir -p ~/festin
cd ~/festin
```

### 2. docker-compose.prod.yml 복사

```bash
# 로컬에서 파일 전송
scp -i your-key.pem docker-compose.prod.yml ec2-user@your-ec2-ip:~/festin/
```

### 3. .env 파일 생성

```bash
# EC2 서버에서 .env 파일 생성
vi ~/festin/.env
```

`.env` 파일 내용 (`.env.prod` 기반):
```bash
SPRING_PROFILES_ACTIVE=prod

# Docker Image (Docker Hub에서 가져올 이미지)
DOCKER_IMAGE=your-dockerhub-username/festin:latest

# Database (AWS RDS)
DB_HOST=festin-mysql.clg4koso8wnj.ap-northeast-2.rds.amazonaws.com
DB_PORT=3306
DB_NAME=festin
DB_USER=admin
DB_PASSWORD=festin-password

# Redis (Container)
REDIS_HOST=redis
REDIS_PORT=6379

# RabbitMQ (Container)
RABBITMQ_HOST=rabbitmq
RABBITMQ_PORT=5672
RABBITMQ_USER=guest
RABBITMQ_PASSWORD=guest
```

**중요**: `DOCKER_IMAGE`를 실제 Docker Hub 사용자명으로 변경하세요!

### 4. Docker 및 Docker Compose 설치 확인

```bash
# Docker 버전 확인
docker --version

# Docker Compose 버전 확인
docker compose version

# 설치되지 않았다면 설치
sudo yum update -y  # Amazon Linux
sudo yum install -y docker
sudo systemctl start docker
sudo systemctl enable docker
sudo usermod -aG docker ec2-user

# Docker Compose V2 설치
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose
```

### 5. 보안 그룹 설정 확인

EC2 인스턴스의 보안 그룹에서 다음 포트를 허용했는지 확인:
- **8080**: 애플리케이션 (필요시 80/443으로 포워딩)
- **22**: SSH (GitHub Actions에서 배포용)

---

## CI/CD 워크플로우

### CI 워크플로우 (`.github/workflows/ci.yml`)

**트리거**:
- PR이 main 브랜치로 생성/업데이트될 때
- main 브랜치에 직접 push될 때

**단계**:
1. 코드 체크아웃
2. JDK 17 설정
3. Gradle 캐싱
4. 빌드 및 테스트 실행
5. 테스트 결과 업로드

### CD 워크플로우 (`.github/workflows/cd.yml`)

**트리거**:
- main 브랜치에 push될 때 (수동 실행도 가능)

**단계**:
1. 코드 체크아웃
2. Docker Buildx 설정
3. Docker Hub 로그인
4. 이미지 메타데이터 생성 (latest + commit SHA 태그)
5. Docker 이미지 빌드 및 푸시
6. EC2 서버 배포:
   - 최신 이미지 pull
   - 기존 컨테이너 중지
   - 새 이미지로 재시작
   - 헬스체크

---

## 배포 테스트

### 1. 로컬에서 변경사항 커밋

```bash
git add .
git commit -m "feat: Add new feature"
git push origin feature-branch
```

### 2. PR 생성

- GitHub에서 PR 생성
- **CI 워크플로우 자동 실행** (빌드 & 테스트)
- Actions 탭에서 진행 상황 확인

### 3. main 브랜치로 머지

- PR 리뷰 후 main 브랜치로 머지
- **CD 워크플로우 자동 실행** (빌드 & 배포)

### 4. 배포 확인

```bash
# EC2 서버에서 확인
ssh -i your-key.pem ec2-user@your-ec2-ip

# 컨테이너 실행 상태 확인
docker ps

# 애플리케이션 로그 확인
docker logs -f festin-app

# 헬스체크
curl http://localhost:8080/actuator/health
```

### 5. 외부에서 접속 확인

```bash
# 로컬에서 확인
curl http://your-ec2-ip:8080/actuator/health
```

---

## 트러블슈팅

### 1. Docker Hub 로그인 실패

**증상**: `Error: unauthorized: authentication required`

**해결**:
- `DOCKER_USERNAME`과 `DOCKER_PASSWORD` Secrets 확인
- Docker Hub 액세스 토큰 사용 권장 (비밀번호 대신)

### 2. EC2 SSH 접속 실패

**증상**: `Permission denied (publickey)`

**해결**:
- `EC2_SSH_PRIVATE_KEY` Secret 확인
- 키 파일 형식 확인 (`-----BEGIN` ~ `-----END` 포함)
- EC2 보안 그룹에서 22번 포트 허용 확인

### 3. 이미지 Pull 실패

**증상**: `Error response from daemon: pull access denied`

**해결**:
- EC2 서버의 `.env` 파일에서 `DOCKER_IMAGE` 값 확인
- Docker Hub에 이미지가 정상 푸시되었는지 확인

### 4. 헬스체크 실패

**증상**: `curl: (7) Failed to connect`

**해결**:
- RDS 연결 정보 확인 (`.env` 파일)
- RDS 보안 그룹에서 EC2 IP 허용 확인
- 애플리케이션 로그 확인: `docker logs festin-app`

---

## 추가 최적화

### 1. 롤백 스크립트

특정 커밋 버전으로 롤백:
```bash
# EC2 서버에서
cd ~/festin
export DOCKER_IMAGE=your-username/festin:<commit-sha>
docker-compose -f docker-compose.prod.yml down app
docker-compose -f docker-compose.prod.yml up -d app
```

### 2. 블루-그린 배포

무중단 배포를 위한 블루-그린 전략은 추후 Kubernetes 또는 ECS로 전환 시 적용 가능합니다.

### 3. 모니터링 추가

- Spring Boot Actuator metrics 활용
- CloudWatch 로그 그룹 연결
- 알림 설정 (SNS + Lambda)

---

## 문의

CI/CD 관련 문제가 발생하면 GitHub Issues를 통해 문의하세요.



⏺ 🚀 CI/CD 설정 단계별 가이드

1️⃣ Docker Hub 계정 생성 및 액세스 토큰 발급

1-1. Docker Hub 계정 만들기 (없다면)
https://hub.docker.com/signup

1-2. 액세스 토큰 생성
1. Docker Hub 로그인
2. 우측 상단 프로필 클릭 → Account Settings
3. 좌측 메뉴 → Security
4. New Access Token 클릭
5. Token Description: festin-github-actions
6. Access permissions: Read, Write, Delete 선택
7. Generate 클릭
8. 생성된 토큰을 복사하여 저장 (다시 볼 수 없습니다!)

  ---
2️⃣ GitHub Secrets 설정

GitHub 저장소로 이동:
Settings > Secrets and variables > Actions > New repository secret

다음 6개 Secrets를 추가하세요:

| Secret Name         | 값                      | 설명                                                       |
  |---------------------|-------------------------|------------------------------------------------------------|
| DOCKER_USERNAME     | your-dockerhub-username | Docker Hub 사용자명 (예: injiwon)                          |
| DOCKER_PASSWORD     | dckr_pat_xxxxxxxxx      | 방금 생성한 액세스 토큰                                    |
| EC2_HOST            | 13.125.xxx.xxx          | EC2 퍼블릭 IP 주소                                         |
| EC2_USERNAME        | ec2-user 또는 ubuntu    | SSH 접속 사용자명 (Amazon Linux: ec2-user, Ubuntu: ubuntu) |
| EC2_SSH_PRIVATE_KEY | 전체 키 내용            | 아래 명령어로 복사 ⬇️                                      |

EC2_SSH_PRIVATE_KEY 복사 방법:
# macOS
cat ~/.ssh/your-ec2-key.pem | pbcopy

# Linux
cat ~/.ssh/your-ec2-key.pem | xclip -selection clipboard

# Windows (Git Bash)
cat ~/.ssh/your-ec2-key.pem | clip

중요: -----BEGIN RSA PRIVATE KEY-----부터 -----END RSA PRIVATE KEY-----까지 전체를 복사하세요!

  ---
3️⃣ EC2 서버 .env 파일 업데이트

EC2 서버에 접속:
ssh -i ~/.ssh/your-ec2-key.pem ec2-user@your-ec2-ip
cd ~/festin

기존 .env 파일 수정:
vi .env

다음 내용으로 수정 (Docker Hub 사용자명 변경 필수!):
SPRING_PROFILES_ACTIVE=prod

# Docker Image - 본인의 Docker Hub 사용자명으로 변경!!!
DOCKER_IMAGE=your-dockerhub-username/festin:latest

# Database (AWS RDS)
DB_HOST=festin-mysql.clg4koso8wnj.ap-northeast-2.rds.amazonaws.com
DB_PORT=3306
DB_NAME=festin
DB_USER=admin
DB_PASSWORD=festin-password

# Redis (Container)
REDIS_HOST=redis
REDIS_PORT=6379

# RabbitMQ (Container)
RABBITMQ_HOST=rabbitmq
RABBITMQ_PORT=5672
RABBITMQ_USER=guest
RABBITMQ_PASSWORD=guest

저장 및 종료: ESC → :wq → Enter

  ---
4️⃣ 워크플로우 테스트

4-1. 변경사항 커밋 및 푸시
# 로컬에서
git add .
git commit -m "chore: Setup CI/CD with Docker Hub"
git push origin chore/ci-cd

4-2. PR 생성
1. GitHub 저장소로 이동
2. Pull requests 탭
3. New pull request 클릭
4. base: main ← compare: chore/ci-cd
5. Create pull request

4-3. CI 워크플로우 확인
- Actions 탭으로 이동
- "CI - Build and Test" 워크플로우가 자동 실행됨
- 빌드 및 테스트가 성공하는지 확인 ✅

4-4. main 브랜치로 머지
- PR에서 Merge pull request 클릭
- CD 워크플로우 자동 시작!

4-5. CD 워크플로우 확인
- Actions 탭에서 "CD - Deploy to AWS EC2" 확인
- 진행 상황:
  a. ✅ Docker 이미지 빌드
  b. ✅ Docker Hub 푸시
  c. ✅ EC2 배포
  d. ✅ 헬스체크

  ---
5️⃣ 배포 확인

EC2 서버에서 확인:
ssh -i ~/.ssh/your-ec2-key.pem ec2-user@your-ec2-ip

# 컨테이너 상태 확인
docker ps

# 애플리케이션 로그 확인
docker logs -f festin-app

# 헬스체크
curl http://localhost:8080/actuator/health

외부에서 확인:
# 로컬에서
curl http://your-ec2-ip:8080/actuator/health

  ---
📋 체크리스트

설정이 완료되면 체크하세요:

- Docker Hub 계정 생성 및 액세스 토큰 발급
- GitHub Secrets 6개 추가 완료
- EC2 .env 파일에 DOCKER_IMAGE 설정
- PR 생성하여 CI 워크플로우 테스트
- main 머지하여 CD 워크플로우 테스트
- EC2에서 컨테이너 정상 동작 확인
- 헬스체크 응답 확인

  ---
🎯 다음 단계

모든 설정이 완료되면:

1. 코드 변경 → PR 생성 → CI 자동 실행 (빌드 & 테스트)
2. main 머지 → CD 자동 실행 (배포)
3. EC2에서 자동 업데이트 완료!

문제가 발생하면 CICD_SETUP.md 파일의 트러블슈팅 섹션을 참고하세요.
