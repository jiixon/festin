package com.festin.app.booth.application.port.in.dto;

import com.festin.app.booth.domain.model.BoothStatus;

import java.time.LocalTime;

public record BoothDetailResult(
        Long boothId,
        String boothName,
        String description,
        String universityName,
        BoothStatus status,
        int capacity,
        int currentPeople,
        int totalWaiting,
        int estimatedWaitTime,
        LocalTime openTime,
        LocalTime closeTime
) {
}