Feature: Health Check and Infrastructure Connectivity
  Spring Boot 애플리케이션과 모든 인프라(MySQL, Redis, RabbitMQ)가 정상 동작하는지 검증

  Scenario: Health Check API returns ok
    Given 애플리케이션이 실행중이다
    When "/api/health" 엔드포인트를 호출한다
    Then 응답 status는 "ok"이다

  Scenario: MySQL database is accessible
    Given 애플리케이션이 실행중이다
    When MySQL 데이터베이스에 연결을 시도한다
    Then MySQL 연결이 성공한다

  Scenario: Redis cache is accessible
    Given 애플리케이션이 실행중이다
    When Redis에 데이터를 저장하고 조회한다
    Then Redis 연결이 성공한다

  Scenario: RabbitMQ message queue is accessible
    Given 애플리케이션이 실행중이다
    When RabbitMQ 연결을 테스트한다
    Then RabbitMQ가 정상 동작한다
