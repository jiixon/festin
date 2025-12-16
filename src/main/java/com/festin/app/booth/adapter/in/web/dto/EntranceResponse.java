package com.festin.app.booth.adapter.in.web.dto;

import com.festin.app.waiting.application.port.in.result.EntranceResult;
import com.festin.app.waiting.domain.model.WaitingStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 입장 확인 응답 DTO
 *
 * API 스펙:
 * {
 *   "waitingId": 123,
 *   "status": "ENTERED",
 *   "enteredAt": "2025-11-20T10:50:00Z"
 * }
 */
@Getter
@AllArgsConstructor
public class EntranceResponse {
    private Long waitingId;
    private WaitingStatus status;
    private LocalDateTime enteredAt;

    public static EntranceResponse from(EntranceResult result) {
        return new EntranceResponse(
                result.waitingId(),
                result.status(),
                result.enteredAt()
        );
    }
}