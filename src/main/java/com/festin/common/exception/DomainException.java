package com.festin.app.common.exception;

import lombok.Getter;

/**
 * Domain Exception 추상 클래스
 *
 * 모든 도메인 예외의 부모 클래스
 * ErrorCode를 포함하여 표준화된 에러 응답 제공
 */
@Getter
public abstract class DomainException extends RuntimeException {

    private final ErrorCode errorCode;

    protected DomainException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    protected DomainException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
    }

    protected DomainException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }
}