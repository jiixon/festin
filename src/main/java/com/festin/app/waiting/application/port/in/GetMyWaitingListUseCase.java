package com.festin.app.waiting.application.port.in;

import com.festin.app.waiting.application.port.in.result.MyWaitingListResult;

/**
 * 내 대기 목록 조회 UseCase
 *
 * 책임:
 * - 사용자의 활성 대기 목록 조회
 * - 각 대기의 순번, 상태, 예상 시간 정보 제공
 */
public interface GetMyWaitingListUseCase {

    /**
     * 내 대기 목록 조회
     *
     * @param userId 사용자 ID
     * @return 대기 목록
     */
    MyWaitingListResult getMyWaitingList(Long userId);
}
