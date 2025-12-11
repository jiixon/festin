package com.festin.app.domain.exception;

/**
 * 이미 해당 부스에 대기 등록된 경우 발생하는 예외
 *
 * 비즈니스 규칙:
 * - 같은 부스에 중복 등록 불가
 * - 당일 내 재등록 불가 (멱등성)
 */
public class AlreadyRegisteredException extends RuntimeException {

    public AlreadyRegisteredException(String message) {
        super(message);
    }

    public AlreadyRegisteredException() {
        super("이미 해당 부스에 대기 중입니다.");
    }
}