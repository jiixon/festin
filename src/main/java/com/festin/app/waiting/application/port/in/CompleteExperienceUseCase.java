package com.festin.app.waiting.application.port.in;

import com.festin.app.waiting.application.port.in.result.CompleteResult;
import com.festin.app.waiting.domain.exception.InvalidStatusException;
import com.festin.app.waiting.domain.exception.WaitingNotFoundException;

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
     * @return 체험 완료 결과
     * @throws WaitingNotFoundException 대기 정보 없음
     * @throws InvalidStatusException 잘못된 상태
     */
    CompleteResult complete(Long boothId, Long waitingId);
}