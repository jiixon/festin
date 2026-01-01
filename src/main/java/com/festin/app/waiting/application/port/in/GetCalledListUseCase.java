package com.festin.app.waiting.application.port.in;

import com.festin.app.waiting.application.port.in.result.CalledListResult;

/**
 * 호출 대기 목록 조회 UseCase
 *
 * 스태프가 부스의 호출된 대기 목록을 조회하는 기능
 */
public interface GetCalledListUseCase {

    /**
     * 부스의 호출된 대기 목록 조회
     *
     * @param boothId 부스 ID
     * @return 호출된 대기 목록 (사용자 정보, 남은 시간 포함)
     */
    CalledListResult getCalledList(Long boothId);
}