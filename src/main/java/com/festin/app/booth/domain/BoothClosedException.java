package com.festin.app.booth.domain;

import com.festin.app.common.exception.DomainException;
import com.festin.app.common.exception.ErrorCode;

/**
 * 부스가 운영 중이 아닌 경우 발생하는 예외
 *
 * 비즈니스 규칙:
 * - 부스가 OPEN 상태일 때만 대기 등록 가능
 */
public class BoothClosedException extends DomainException {

    public BoothClosedException() {
        super(ErrorCode.BOOTH_CLOSED);
    }
}