package com.festin.app.booth.application.port.in.dto;

import com.festin.app.booth.domain.model.BoothStatus;

import java.util.List;

public record BoothListResult(
        List<BoothItem> booths
) {
    public record BoothItem(
            Long boothId,
            String boothName,
            String description,
            String universityName,
            BoothStatus status,
            int capacity,
            int currentWaiting,
            int estimatedWaitTime
    ) {
    }
}
