package com.festin.app.user.domain;

import com.festin.app.common.exception.DomainException;
import com.festin.app.common.exception.ErrorCode;

/**
 * FCM 토큰이 유효하지 않은 경우 발생하는 예외
 *
 * 발생 상황:
 * - FCM 토큰이 null인 경우
 * - FCM 토큰이 빈 문자열인 경우
 */
public class InvalidFcmTokenException extends DomainException {

    public InvalidFcmTokenException() {
        super(ErrorCode.INVALID_FCM_TOKEN, "FCM 토큰은 필수입니다.");
    }
}