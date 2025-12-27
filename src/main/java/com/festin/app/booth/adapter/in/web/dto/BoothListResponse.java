package com.festin.app.booth.adapter.in.web.dto;

import com.festin.app.booth.application.port.in.dto.BoothListResult;

import java.util.List;
import java.util.stream.Collectors;

public record BoothListResponse(
        List<BoothItem> booths
) {
    public static BoothListResponse from(BoothListResult result) {
        List<BoothItem> boothItems = result.booths().stream()
                .map(item -> new BoothItem(
                        item.boothId(),
                        item.boothName(),
                        item.description(),
                        item.universityName(),
                        item.status().name(),
                        item.capacity(),
                        item.currentWaiting(),
                        item.estimatedWaitTime()
                ))
                .collect(Collectors.toList());

        return new BoothListResponse(boothItems);
    }

    public record BoothItem(
            Long boothId,
            String boothName,
            String description,
            String universityName,
            String status,
            int capacity,
            int currentWaiting,
            int estimatedWaitTime
    ) {
    }
}