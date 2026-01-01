package com.festin.app.booth.application.port.in.dto;

/**
 * 부스 현황 조회 결과 (스태프용)
 *
 * 실시간 정보 (Redis):
 * - boothId, boothName, currentPeople, capacity, totalWaiting
 *
 * 오늘 통계 (MySQL):
 * - totalCalled: 오늘 호출된 총 인원
 * - totalEntered: 오늘 입장한 총 인원 (ENTERED 상태)
 * - totalNoShow: 오늘 노쇼 총 인원 (COMPLETED + NO_SHOW)
 * - totalCompleted: 오늘 정상 완료 총 인원 (COMPLETED + ENTERED)
 */
public record BoothStatusResult(
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
}
