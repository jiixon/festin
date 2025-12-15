package com.festin.waiting.domain.exception;

import com.festin.common.exception.DomainException;
import com.festin.common.exception.ErrorCode;

/**
 * 이미 해당 부스에 대기 등록된 경우 발생하는 예외
 *
 * 비즈니스 규칙:
 * - 같은 부스에 중복 등록 불가
 * - 당일 내 재등록 불가 (멱등성)
 */
public class AlreadyRegisteredException extends DomainException {

    public AlreadyRegisteredException() {
        super(ErrorCode.ALREADY_REGISTERED);
    }
}