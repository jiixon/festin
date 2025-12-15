package com.festin.app.booth.domain;

import com.festin.app.common.exception.DomainException;
import com.festin.app.common.exception.ErrorCode;

/**
 * 부스를 찾을 수 없는 경우 발생하는 예외
 *
 * 발생 상황:
 * - 존재하지 않는 부스 ID로 조회 시도
 * - 삭제된 부스 접근 시도
 */
public class BoothNotFoundException extends DomainException {

    public BoothNotFoundException() {
        super(ErrorCode.BOOTH_NOT_FOUND);
    }
}