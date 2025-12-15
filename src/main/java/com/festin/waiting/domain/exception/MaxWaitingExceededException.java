package com.festin.waiting.domain.exception;

import com.festin.common.exception.DomainException;
import com.festin.common.exception.ErrorCode;

/**
 * 최대 대기 가능 부스 수를 초과한 경우 발생하는 예외
 *
 * 비즈니스 규칙:
 * - 사용자는 최대 2개 부스까지만 동시 대기 가능
 * - 공정한 기회 제공 및 독점 방지
 */
public class MaxWaitingExceededException extends DomainException {

    public MaxWaitingExceededException() {
        super(ErrorCode.MAX_WAITING_EXCEEDED);
    }
}