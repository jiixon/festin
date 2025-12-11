package com.festin.app.domain.exception;

/**
 * 대기열 작업 실패 시 발생하는 예외
 *
 * 발생 상황:
 * - 대기열 추가 후 순번 조회 실패
 * - Redis 대기열 작업 중 일관성 문제 발생
 */
public class QueueOperationException extends RuntimeException {

    private QueueOperationException(String message) {
        super(message);
    }

    public static QueueOperationException enqueueFailed() {
        return new QueueOperationException("대기열 추가 후 순번 조회에 실패했습니다.");
    }
}