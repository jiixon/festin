package com.festin.app.booth.application.port.in;

import com.festin.app.booth.application.port.in.dto.BoothDetailResult;

/**
 * 부스 상세 조회 UseCase
 *
 * 책임:
 * - 부스 상세 정보 조회
 * - 현재 입장 인원, 대기 인원, 예상 시간 조합
 */
public interface GetBoothDetailUseCase {

    /**
     * 부스 상세 조회
     *
     * @param boothId 부스 ID
     * @return 부스 상세 정보
     */
    BoothDetailResult getBoothDetail(Long boothId);
}