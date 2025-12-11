package com.festin.app.domain.model;

import java.util.List;

/**
 * 대기 상태
 *
 * 상태 전이:
 * CALLED (호출됨) → ENTERED (입장 확인됨) → COMPLETED (완료됨)
 */
public enum WaitingStatus {
    CALLED,      // 호출됨 - 푸시 알림 발송, 노쇼 타이머 시작
    ENTERED,     // 입장 확인됨 - 스태프가 사용자 도착 확인
    COMPLETED;   // 완료됨 - 체험 종료

    /**
     * 활성 상태 목록 (대기 중 + 입장 확인)
     *
     * 비즈니스 규칙:
     * - 사용자는 최대 2개 부스까지 활성 상태를 가질 수 있음
     * - COMPLETED는 활성 상태가 아님
     */
    public static final List<WaitingStatus> ACTIVE_STATUSES = List.of(CALLED, ENTERED);
}