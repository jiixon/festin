package com.festin.app.waiting.application.port.in.result;

import com.festin.app.waiting.domain.model.WaitingStatus;

import java.util.List;

/**
 * 내 대기 목록 조회 결과
 */
public record MyWaitingListResult(
        List<WaitingItem> waitings
) {
    /**
     * 대기 항목
     *
     * status:
     * - null: WAITING (대기 중, 아직 호출 안됨)
     * - CALLED: 호출됨 (입장 대기 중)
     */
    public record WaitingItem(
            Long boothId,
            String boothName,
            int position,
            int totalWaiting,
            int estimatedWaitTime,
            WaitingStatus status,   // null = WAITING, CALLED = 호출됨
            String registeredAt     // ISO-8601 format
    ) {
    }
}