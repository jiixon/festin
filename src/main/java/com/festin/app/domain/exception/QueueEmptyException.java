package com.festin.app.domain.exception;

/**
 * 대기열이 비어있는 경우 발생하는 예외
 *
 * 비즈니스 규칙:
 * - 호출 시 대기 중인 사람이 없으면 호출 불가
 */
public class QueueEmptyException extends RuntimeException {

    public QueueEmptyException(String message) {
        super(message);
    }

    public QueueEmptyException() {
        super("대기 중인 사람이 없습니다.");
    }
}