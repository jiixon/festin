package com.festin.app.waiting.application.port.in.result;

import com.festin.app.waiting.domain.model.CompletionType;
import com.festin.app.waiting.domain.model.WaitingStatus;

import java.time.LocalDateTime;

/**
 * 체험 완료 Result
 *
 * UseCase → Controller로 반환되는 결과 데이터
 */
public record CompleteResult(
    Long waitingId,
    WaitingStatus status,
    CompletionType completionType,
    LocalDateTime completedAt
) {
}