package com.festin.app.adapter.in.web.dto;

import com.festin.app.application.port.in.result.EnqueueResult;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 대기 등록 응답 DTO
 */
@Data
@AllArgsConstructor
public class EnqueueResponse {
    private Long boothId;
    private String boothName;
    private Integer position;
    private Integer totalWaiting;
    private Integer estimatedWaitTime;
    private LocalDateTime registeredAt;

    public static EnqueueResponse from(EnqueueResult result) {
        return new EnqueueResponse(
            result.boothId(),
            result.boothName(),
            result.position(),
            result.totalWaiting(),
            result.estimatedWaitTime(),
            result.registeredAt()
        );
    }
}