package com.festin.app.waiting.application.port.in.result;

import com.festin.app.waiting.domain.model.WaitingStatus;

import java.time.LocalDateTime;

/**
 * 입장 확인 Result
 *
 * UseCase → Controller로 반환되는 결과 데이터
 */
public record EntranceResult(
    Long waitingId,
    WaitingStatus status,
    LocalDateTime enteredAt
) {
}