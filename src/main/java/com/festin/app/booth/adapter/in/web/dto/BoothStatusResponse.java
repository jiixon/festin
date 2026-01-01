package com.festin.app.booth.adapter.in.web.dto;

import com.festin.app.booth.application.port.in.dto.BoothStatusResult;

/**
 * 부스 현황 조회 응답 DTO (스태프용)
 */
public record BoothStatusResponse(
    Long boothId,
    String boothName,
    int currentPeople,
    int capacity,
    int totalWaiting,
    TodayStats todayStats
) {
    /**
     * 오늘 통계
     */
    public record TodayStats(
        int totalCalled,
        int totalEntered,
        int totalNoShow,
        int totalCompleted
    ) {}

    public static BoothStatusResponse from(BoothStatusResult result) {
        TodayStats todayStats = new TodayStats(
            result.todayStats().totalCalled(),
            result.todayStats().totalEntered(),
            result.todayStats().totalNoShow(),
            result.todayStats().totalCompleted()
        );

        return new BoothStatusResponse(
            result.boothId(),
            result.boothName(),
            result.currentPeople(),
            result.capacity(),
            result.totalWaiting(),
            todayStats
        );
    }
}
