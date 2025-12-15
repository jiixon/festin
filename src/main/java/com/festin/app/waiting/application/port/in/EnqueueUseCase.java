package com.festin.app.waiting.application.port.in;

import com.festin.app.booth.domain.BoothClosedException;
import com.festin.app.waiting.application.port.in.command.EnqueueCommand;
import com.festin.app.waiting.application.port.in.result.EnqueueResult;
import com.festin.app.waiting.domain.exception.AlreadyRegisteredException;
import com.festin.app.waiting.domain.exception.MaxWaitingExceededException;

/**
 * 대기 등록 UseCase
 *
 * 비즈니스 요구사항:
 * - 사용자는 최대 2개 부스까지 동시 대기 가능
 * - 같은 부스에 중복 등록 불가
 * - 부스가 운영 중일 때만 등록 가능
 *
 * 처리 흐름:
 * 1. 비즈니스 규칙 검증 (Policy)
 * 2. Redis 대기열에 추가
 * 3. 활성 부스 목록 갱신
 */
public interface EnqueueUseCase {

    /**
     * 대기열에 등록
     *
     * @param command 등록 명령 (userId, boothId)
     * @return 등록 결과 (순번, 대기자 수, 예상 대기 시간)
     * @throws AlreadyRegisteredException 이미 등록된 경우
     * @throws MaxWaitingExceededException 최대 대기 수 초과
     * @throws BoothClosedException 부스 마감 상태
     */
    EnqueueResult enqueue(EnqueueCommand command);
}
