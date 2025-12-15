package com.festin.app.waiting.application.port.in;

import com.festin.app.waiting.application.port.in.result.PositionResult;
import com.festin.app.waiting.domain.exception.WaitingNotFoundException;

/**
 * 순번 조회 UseCase
 *
 * 비즈니스 요구사항:
 * - 사용자가 실시간으로 자신의 대기 순번 확인
 * - Redis에서 빠르게 조회 (50ms 이내)
 *
 * 처리 흐름:
 * 1. Redis 대기열에서 사용자 위치 조회
 * 2. 전체 대기자 수 계산
 * 3. 예상 대기 시간 계산
 */
public interface GetPositionUseCase {

    /**
     * 특정 부스에서 내 순번 조회
     *
     * @param userId 사용자 ID
     * @param boothId 부스 ID
     * @return 순번 조회 결과
     * @throws WaitingNotFoundException 대기 중이 아닌 경우
     */
    PositionResult getPosition(Long userId, Long boothId);
}
