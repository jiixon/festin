package com.festin.app.booth.application.port.in;

import com.festin.app.booth.application.port.in.dto.BoothStatusResult;

/**
 * 부스 현황 조회 UseCase (스태프용)
 *
 * 책임:
 * - 부스의 현재 상태 조회 (실시간 정보 + 오늘 통계)
 * - 스태프 대시보드에서 사용
 *
 * 반환 정보:
 * - 실시간: currentPeople, capacity, totalWaiting, boothName (Redis)
 * - 통계: totalCalled, totalEntered, totalNoShow, totalCompleted (MySQL)
 */
public interface GetBoothStatusUseCase {

    /**
     * 부스 현황 조회
     *
     * @param boothId 부스 ID
     * @return 부스 현황 (실시간 정보 + 오늘 통계)
     */
    BoothStatusResult getBoothStatus(Long boothId);
}
