package com.festin.app.domain.exception;

/**
 * 잘못된 상태 전이를 시도한 경우 발생하는 예외
 *
 * 비즈니스 규칙:
 * - CALLED → ENTERED → COMPLETED 순서 준수
 * - 상태 전이 규칙 위반 시 예외 발생
 */
public class InvalidStatusException extends RuntimeException {

    private InvalidStatusException(String message) {
        super(message);
    }

    /**
     * 호출된 상태가 아닐 때
     * enter(), markAsNoShow(), cancel() 실행 시 발생
     */
    public static InvalidStatusException notCalled() {
        return new InvalidStatusException("호출된 상태가 아닙니다.");
    }

    /**
     * 입장 확인된 상태가 아닐 때
     * complete() 실행 시 발생
     */
    public static InvalidStatusException notEntered() {
        return new InvalidStatusException("입장 확인된 상태가 아닙니다.");
    }
}