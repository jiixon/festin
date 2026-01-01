Feature: 푸시 알림 설정
  사용자가 푸시 알림을 받기 위해 기기를 등록하는 기능

  Background:
    Given 사용자 "user1"이 로그인되어 있다

  Scenario: 처음으로 푸시 알림 활성화
    When "user1"이 기기 토큰을 등록한다
    Then 푸시 알림 활성화에 성공한다
    And "user1"은 이제 푸시 알림을 받을 수 있다

  Scenario: 다른 기기에서 로그인 (토큰 업데이트)
    Given "user1"이 기기 A에서 푸시 알림을 활성화했다
    When "user1"이 기기 B에서 토큰을 등록한다
    Then 기기 B로 푸시 알림을 받을 수 있다
    And 기기 A로는 더 이상 푸시를 받지 않는다

  Scenario: 로그인하지 않은 사용자의 토큰 등록 실패
    Given 로그인하지 않은 상태이다
    When 기기 토큰 등록을 시도한다
    Then 인증 오류가 발생한다