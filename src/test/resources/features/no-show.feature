Feature: 노쇼 처리
  호출 후 일정 시간 내 미입장 시 자동 노쇼 처리되는 기능

  Background:
    Given 테스트용 대학교가 존재한다
    And 테스트용 부스가 존재한다
    And 테스트용 사용자가 존재한다

  Scenario: 타임아웃된 사용자는 자동으로 노쇼 처리된다
    Given 부스에 대기 중인 사용자가 존재한다
    And 스태프가 사용자를 호출했다
    And 5분이 경과했다
    When 노쇼 자동 처리가 실행된다
    Then 사용자의 대기 상태는 "COMPLETED"가 된다
    And 사용자의 완료 유형은 "NO_SHOW"가 된다