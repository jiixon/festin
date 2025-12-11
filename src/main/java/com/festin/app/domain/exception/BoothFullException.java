package com.festin.app.domain.exception;

/**
 * 부스 정원이 초과된 경우 발생하는 예외
 *
 * 비즈니스 규칙:
 * - 부스 현재 인원이 최대 정원에 도달하면 호출 불가
 * - 동시성 제어로 정원 초과 방지 필요
 */
public class BoothFullException extends RuntimeException {

    public BoothFullException(String message) {
        super(message);
    }

    public BoothFullException() {
        super("부스 정원이 초과되었습니다.");
    }
}