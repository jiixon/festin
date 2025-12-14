package com.festin.app.application.port.in;

/**
 * 대기 취소 UseCase
 *
 * 비즈니스 요구사항:
 * - 사용자가 원하지 않으면 대기 취소 가능
 * - 취소 후 다른 부스 등록 가능
 *
 * 처리 흐름:
 * 1. Redis 대기열에서 제거
 * 2. 활성 부스 목록에서 제거
 * 3. 멱등성 키 삭제
 */
public interface CancelWaitingUseCase {

    /**
     * 대기 취소
     *
     * @param userId 사용자 ID
     * @param boothId 부스 ID
     * @throws com.festin.app.domain.exception.WaitingNotFoundException 대기 중이 아닌 경우
     */
    void cancel(Long userId, Long boothId);
}
