package com.festin.app.application.port.in;

/**
 * 체험 완료 UseCase
 *
 * 비즈니스 요구사항:
 * - 스태프가 "완료" 버튼 클릭
 * - 부스 정원 1 감소 (다음 사람 호출 가능)
 *
 * 처리 흐름:
 * 1. Waiting 조회 (MySQL)
 * 2. 상태 검증 (ENTERED → COMPLETED)
 * 3. Domain 로직 실행 (waiting.complete())
 * 4. MySQL 업데이트
 * 5. Redis 부스 현재 인원 -1
 */
public interface CompleteExperienceUseCase {

    /**
     * 체험 완료
     *
     * @param boothId 부스 ID
     * @param waitingId 대기 ID
     * @throws com.festin.app.domain.exception.WaitingNotFoundException 대기 정보 없음
     * @throws com.festin.app.domain.exception.InvalidStatusException 잘못된 상태
     */
    void complete(Long boothId, Long waitingId);
}