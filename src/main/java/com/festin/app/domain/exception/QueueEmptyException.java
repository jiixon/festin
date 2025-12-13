package com.festin.app.domain.exception;

import com.festin.app.common.exception.DomainException;
import com.festin.app.common.exception.ErrorCode;

/**
 * 대기열이 비어있는 경우 발생하는 예외
 *
 * 비즈니스 규칙:
 * - 호출 시 대기 중인 사람이 없으면 호출 불가
 */
public class QueueEmptyException extends DomainException {

    public QueueEmptyException() {
        super(ErrorCode.QUEUE_EMPTY);
    }
}