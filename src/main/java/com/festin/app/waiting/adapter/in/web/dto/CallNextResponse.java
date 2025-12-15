package com.festin.app.waiting.adapter.in.web.dto;

import com.festin.app.waiting.application.port.in.result.CallResult;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 다음 사람 호출 응답 DTO
 */
@Data
@AllArgsConstructor
public class CallNextResponse {
    private Long waitingId;
    private Long userId;
    private Integer position;
    private LocalDateTime calledAt;

    public static CallNextResponse from(CallResult result) {
        return new CallNextResponse(
                result.waitingId(),
                result.userId(),
                result.position(),
                result.calledAt()
        );
    }
}