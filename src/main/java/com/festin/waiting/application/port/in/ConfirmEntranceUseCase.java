package com.festin.app.application.port.in;

/**
 * 입장 확인 UseCase
 *
 * 비즈니스 요구사항:
 * - 스태프가 사용자 확인 후 "입장 확인" 버튼 클릭
 * - 호출된 상태여야 입장 가능
 *
 * 처리 흐름:
 * 1. Waiting 조회 (MySQL)
 * 2. 상태 검증 (CALLED → ENTERED)
 * 3. Domain 로직 실행 (waiting.enter())
 * 4. MySQL 업데이트
 */
public interface ConfirmEntranceUseCase {

    /**
     * 입장 확인
     *
     * @param boothId 부스 ID
     * @param waitingId 대기 ID
     * @throws com.festin.app.domain.exception.WaitingNotFoundException 대기 정보 없음
     * @throws com.festin.app.domain.exception.InvalidStatusException 잘못된 상태
     */
    void confirmEntrance(Long boothId, Long waitingId);
}
