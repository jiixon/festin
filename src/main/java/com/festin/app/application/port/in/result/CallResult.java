package com.festin.app.application.port.in.result;

import java.time.LocalDateTime;

/**
 * 호출 Result
 *
 * UseCase → Controller로 반환되는 결과 데이터
 */
public record CallResult(
    Long waitingId,
    Long userId,
    Integer position,
    LocalDateTime calledAt
) {
}