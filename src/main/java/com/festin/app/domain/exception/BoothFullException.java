package com.festin.app.domain.exception;

import com.festin.app.common.exception.DomainException;
import com.festin.app.common.exception.ErrorCode;

/**
 * 부스 정원이 초과된 경우 발생하는 예외
 *
 * 비즈니스 규칙:
 * - 부스 현재 인원이 최대 정원에 도달하면 호출 불가
 * - 동시성 제어로 정원 초과 방지 필요
 */
public class BoothFullException extends DomainException {

    public BoothFullException() {
        super(ErrorCode.BOOTH_FULL);
    }
}