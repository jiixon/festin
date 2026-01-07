package com.festin.app.waiting.application.port.in.result;

import com.festin.app.waiting.domain.model.Waiting;
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
    public static EntranceResult from(Waiting waiting) {
        return new EntranceResult(
                waiting.getId(),
                waiting.getStatus(),
                waiting.getEnteredAt()
        );
    }
}