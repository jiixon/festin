package com.festin.app.booth.application.port.out.dto;

import com.festin.app.booth.domain.model.BoothStatus;

/**
 * Booth Read Model
 *
 * 책임:
 * - 부스 조회용 불변 데이터 전달
 * - Entity의 표현용 정보 포함
 */
public record BoothInfo(
        Long id,
        String name,
        String description,
        Long universityId,
        String universityName,
        int capacity,
        BoothStatus status
) {
}