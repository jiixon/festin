package com.festin.app.domain.exception;

/**
 * 잘못된 상태 전이를 시도한 경우 발생하는 예외
 *
 * 비즈니스 규칙:
 * - CALLED → ENTERED → COMPLETED 순서 준수
 * - 상태 전이 규칙 위반 시 예외 발생
 */
public class InvalidStatusException extends RuntimeException {

    public InvalidStatusException(String message) {
        super(message);
    }
}