# ====================================
# Multi-stage build for Spring Boot application
# - Builder stage: Gradle 빌드
# - Runtime stage: JRE만 포함 (경량화)
# ====================================

# ===== Builder Stage =====
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# Gradle Wrapper 복사
COPY gradlew .
COPY gradle gradle/

# Gradle 파일 복사 (의존성 정의)
COPY build.gradle settings.gradle ./

# 의존성 다운로드 (변경 시에만 재실행)
# - 소스 코드 변경 시에도 의존성은 캐시 재사용
RUN ./gradlew dependencies --no-daemon || true

# 소스 코드 복사 (변경 빈도 높음 → 마지막에 복사)
COPY src ./src

# 빌드 (테스트 제외로 속도 향상)
# - CI에서 이미 테스트 완료
# - CD에서는 빌드만 수행
RUN ./gradlew bootJar -x test --no-daemon

# ===== Runtime Stage =====
FROM eclipse-temurin:21-jre
WORKDIR /app

# JAR 파일만 복사 (경량 이미지)
COPY --from=builder /app/build/libs/*.jar app.jar

# 포트 노출
EXPOSE 8080

# 애플리케이션 실행
ENTRYPOINT ["java", \
    "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-local}", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-jar", "app.jar"]