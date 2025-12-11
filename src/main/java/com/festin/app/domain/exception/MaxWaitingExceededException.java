package com.festin.app.domain.exception;

/**
 * 최대 대기 가능 부스 수를 초과한 경우 발생하는 예외
 *
 * 비즈니스 규칙:
 * - 사용자는 최대 2개 부스까지만 동시 대기 가능
 * - 공정한 기회 제공 및 독점 방지
 */
public class MaxWaitingExceededException extends RuntimeException {

    private static final int MAX_ACTIVE_BOOTHS = 2;

    public MaxWaitingExceededException() {
        super(String.format("최대 %d개 부스까지만 대기 가능합니다.", MAX_ACTIVE_BOOTHS));
    }

    public MaxWaitingExceededException(String message) {
        super(message);
    }
}