package com.festin.app.booth.adapter.in.web.dto;

import com.festin.app.waiting.application.port.in.result.CompleteResult;
import com.festin.app.waiting.domain.model.CompletionType;
import com.festin.app.waiting.domain.model.WaitingStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 체험 완료 응답 DTO
 *
 * API 스펙:
 * {
 *   "waitingId": 123,
 *   "status": "COMPLETED",
 *   "completionType": "ENTERED",
 *   "completedAt": "2025-11-20T10:55:00Z"
 * }
 */
@Getter
@AllArgsConstructor
public class CompleteResponse {
    private Long waitingId;
    private WaitingStatus status;
    private CompletionType completionType;
    private LocalDateTime completedAt;

    public static CompleteResponse from(CompleteResult result) {
        return new CompleteResponse(
                result.waitingId(),
                result.status(),
                result.completionType(),
                result.completedAt()
        );
    }
}