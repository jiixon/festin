Feature: 간단 로그인 (자동 회원가입)
  Email 기반 간단 로그인 기능 검증

  Scenario: 신규 사용자 로그인 (자동 회원가입)
    Given 애플리케이션이 실행중이다
    When 사용자가 "newuser@example.com", "홍길동", "VISITOR"로 로그인을 요청한다
    Then 로그인이 성공하고 JWT 토큰을 받는다
    And 응답에 userId, email, nickname, role이 포함된다

  Scenario: 기존 사용자 로그인 (닉네임 업데이트)
    Given 애플리케이션이 실행중이다
    And "existing@example.com", "김철수", "VISITOR"로 이미 가입된 사용자가 있다
    When 사용자가 "existing@example.com", "김영희", "VISITOR"로 로그인을 요청한다
    Then 로그인이 성공하고 JWT 토큰을 받는다
    And 닉네임이 "김영희"로 업데이트된다

  Scenario: JWT 토큰에 사용자 정보 포함 확인
    Given 애플리케이션이 실행중이다
    When 사용자가 "tokentest@example.com", "토큰테스트", "STAFF"로 로그인을 요청한다
    Then 로그인이 성공하고 JWT 토큰을 받는다
    And JWT 토큰에 userId, email, role 정보가 포함된다
