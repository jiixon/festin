package com.festin.waiting.adapter.in.web.dto;

import com.festin.waiting.application.port.in.result.PositionResult;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 순번 조회 응답 DTO
 */
@Data
@AllArgsConstructor
public class PositionResponse {
    private Long boothId;
    private String boothName;
    private Integer position;
    private Integer totalWaiting;
    private Integer estimatedWaitTime;

    public static PositionResponse from(PositionResult result) {
        return new PositionResponse(
            result.boothId(),
            result.boothName(),
            result.position(),
            result.totalWaiting(),
            result.estimatedWaitTime()
        );
    }
}