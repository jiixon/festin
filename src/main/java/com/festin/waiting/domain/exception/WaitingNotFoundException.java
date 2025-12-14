package com.festin.app.domain.exception;

import com.festin.app.common.exception.DomainException;
import com.festin.app.common.exception.ErrorCode;

/**
 * 대기 정보를 찾을 수 없는 경우 발생하는 예외
 */
public class WaitingNotFoundException extends DomainException {

    public WaitingNotFoundException() {
        super(ErrorCode.WAITING_NOT_FOUND);
    }
}