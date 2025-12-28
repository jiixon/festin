package com.festin.app.waiting.adapter.in.web.dto;

import com.festin.app.waiting.application.port.in.result.MyWaitingListResult;

import java.util.List;

/**
 * 내 대기 목록 조회 응답
 */
public record MyWaitingListResponse(
        List<WaitingItemResponse> waitings
) {
    /**
     * 대기 항목 응답
     *
     * status:
     * - "WAITING": 대기 중 (아직 호출 안됨)
     * - "CALLED": 호출됨 (입장 대기 중)
     */
    public record WaitingItemResponse(
            Long boothId,
            String boothName,
            int position,
            int totalWaiting,
            int estimatedWaitTime,
            String status,          // "WAITING" or "CALLED"
            String registeredAt     // ISO-8601 format
    ) {
    }

    /**
     * Result → Response 변환
     *
     * WaitingStatus 변환 규칙:
     * - null → "WAITING" (Redis 대기열)
     * - CALLED → "CALLED" (MySQL 저장)
     */
    public static MyWaitingListResponse from(MyWaitingListResult result) {
        List<WaitingItemResponse> items = result.waitings().stream()
                .map(item -> new WaitingItemResponse(
                        item.boothId(),
                        item.boothName(),
                        item.position(),
                        item.totalWaiting(),
                        item.estimatedWaitTime(),
                        item.status() == null ? "WAITING" : item.status().name(),
                        item.registeredAt()
                ))
                .toList();

        return new MyWaitingListResponse(items);
    }
}