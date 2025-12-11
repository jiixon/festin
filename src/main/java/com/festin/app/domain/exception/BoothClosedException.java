package com.festin.app.domain.exception;

/**
 * 부스가 운영 중이 아닌 경우 발생하는 예외
 *
 * 비즈니스 규칙:
 * - 부스가 OPEN 상태일 때만 대기 등록 가능
 */
public class BoothClosedException extends RuntimeException {

    public BoothClosedException(String message) {
        super(message);
    }

    public BoothClosedException() {
        super("부스가 운영 중이 아닙니다.");
    }
}