Feature: Waiting Queue API
  Users can register for booth queues and check their position

  Background:
    Given 테스트용 대학교가 존재한다
    And 테스트용 부스가 존재한다
    And 테스트용 사용자가 존재한다

  Scenario: Successfully enqueue to booth
    Given 사용자가 로그인되어 있다
    When 사용자가 부스에 대기 등록을 요청한다
    Then 대기 등록이 성공한다
    And 응답 상태 코드는 201이다
    And 응답에 부스 정보가 포함된다
    And 응답에 순번 정보가 포함된다

  Scenario: Successfully check position
    Given 사용자가 부스에 이미 대기 등록되어 있다
    When 사용자가 부스의 순번을 조회한다
    Then 순번 조회가 성공한다
    And 응답 상태 코드는 200이다
    And 응답에 부스 정보가 포함된다
    And 응답에 순번 정보가 포함된다