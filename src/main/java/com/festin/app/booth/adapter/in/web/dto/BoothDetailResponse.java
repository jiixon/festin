package com.festin.app.booth.adapter.in.web.dto;

import com.festin.app.booth.application.port.in.dto.BoothDetailResult;
import com.festin.app.booth.domain.model.BoothStatus;

import java.time.LocalTime;

/**
 * 부스 상세 조회 응답 DTO
 */
public record BoothDetailResponse(
        Long boothId,
        String boothName,
        String description,
        String universityName,
        String status,
        int capacity,
        int currentPeople,
        int totalWaiting,
        int estimatedWaitTime,
        String openTime,
        String closeTime
) {
    public static BoothDetailResponse from(BoothDetailResult result) {
        return new BoothDetailResponse(
                result.boothId(),
                result.boothName(),
                result.description(),
                result.universityName(),
                result.status().name(),
                result.capacity(),
                result.currentPeople(),
                result.totalWaiting(),
                result.estimatedWaitTime(),
                result.openTime() != null ? result.openTime().toString() : null,
                result.closeTime() != null ? result.closeTime().toString() : null
        );
    }
}