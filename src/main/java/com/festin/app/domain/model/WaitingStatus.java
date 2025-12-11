package com.festin.app.domain.model;

/**
 * 대기 상태
 *
 * 상태 전이:
 * CALLED (호출됨) → ENTERED (입장 확인됨) → COMPLETED (완료됨)
 */
public enum WaitingStatus {
    CALLED,      // 호출됨 - 푸시 알림 발송, 노쇼 타이머 시작
    ENTERED,     // 입장 확인됨 - 스태프가 사용자 도착 확인
    COMPLETED    // 완료됨 - 체험 종료
}