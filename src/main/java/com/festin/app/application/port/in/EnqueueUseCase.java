package com.festin.app.application.port.in;

import com.festin.app.application.port.in.command.EnqueueCommand;
import com.festin.app.application.port.in.result.EnqueueResult;

/**
 * 대기 등록 UseCase
 *
 * 비즈니스 요구사항:
 * - 사용자는 최대 2개 부스까지 동시 대기 가능
 * - 같은 부스에 중복 등록 불가
 * - 당일 내 같은 부스 재등록 불가 (멱등성)
 * - 부스가 운영 중일 때만 등록 가능
 *
 * 처리 흐름:
 * 1. 비즈니스 규칙 검증 (Policy)
 * 2. Redis 대기열에 추가
 * 3. 멱등성 키 저장 (TTL 24시간)
 * 4. 활성 부스 목록 갱신
 */
public interface EnqueueUseCase {

    /**
     * 대기열에 등록
     *
     * @param command 등록 명령 (userId, boothId)
     * @return 등록 결과 (순번, 대기자 수, 예상 대기 시간)
     * @throws com.festin.app.domain.exception.AlreadyRegisteredException 이미 등록된 경우
     * @throws com.festin.app.domain.exception.MaxWaitingExceededException 최대 대기 수 초과
     * @throws com.festin.app.domain.exception.BoothClosedException 부스 마감 상태
     */
    EnqueueResult enqueue(EnqueueCommand command);
}
