package com.festin.waiting.domain.exception;

import com.festin.common.exception.DomainException;
import com.festin.common.exception.ErrorCode;

/**
 * 대기 정보를 찾을 수 없는 경우 발생하는 예외
 */
public class WaitingNotFoundException extends DomainException {

    public WaitingNotFoundException() {
        super(ErrorCode.WAITING_NOT_FOUND);
    }

    public WaitingNotFoundException(String message) {
        super(ErrorCode.WAITING_NOT_FOUND, message);
    }

    public static WaitingNotFoundException notInQueue(Long userId, Long boothId) {
        return new WaitingNotFoundException(
                String.format("사용자(%d)가 부스(%d)의 대기열에 없습니다.", userId, boothId)
        );
    }
}