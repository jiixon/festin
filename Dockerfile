# Multi-stage build for Spring Boot application
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# Gradle Wrapper 복사
COPY gradlew .
COPY gradle gradle/

# build.gradle, settings.gradle 복사
COPY build.gradle settings.gradle ./

# 의존성 다운로드 (캐싱)
RUN ./gradlew dependencies --no-daemon || true

# 소스 코드 복사
COPY src ./src

# 빌드
RUN ./gradlew bootJar --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy JAR from builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Expose port
EXPOSE 8080

# Run application with Spring Profile
ENTRYPOINT ["java", "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-local}", "-jar", "app.jar"]