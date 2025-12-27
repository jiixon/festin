package com.festin.app.booth.application.port.in;

import com.festin.app.booth.application.port.in.dto.BoothListResult;

/**
 * 부스 목록 조회 UseCase
 *
 * 책임:
 * - 부스 목록 조회 (전체 또는 대학별)
 * - 대기 인원 및 예상 대기 시간 조합
 */
public interface GetBoothListUseCase {

    /**
     * 부스 목록 조회
     *
     * @param universityId 대학 ID (optional, null이면 전체 조회)
     * @return 부스 목록
     */
    BoothListResult getBoothList(Long universityId);
}