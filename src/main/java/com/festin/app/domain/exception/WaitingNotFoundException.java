package com.festin.app.domain.exception;

/**
 * 대기 정보를 찾을 수 없는 경우 발생하는 예외
 */
public class WaitingNotFoundException extends RuntimeException {

    public WaitingNotFoundException(String message) {
        super(message);
    }

    public WaitingNotFoundException() {
        super("대기 정보를 찾을 수 없습니다.");
    }
}