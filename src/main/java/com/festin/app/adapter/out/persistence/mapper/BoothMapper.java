package com.festin.app.adapter.out.persistence.mapper;

import com.festin.app.adapter.out.persistence.entity.BoothEntity;
import com.festin.app.domain.model.Booth;
import org.springframework.stereotype.Component;

/**
 * Booth Mapper
 *
 * Entity ↔ Domain 변환
 */
@Component
public class BoothMapper {

    /**
     * Entity → Domain
     */
    public Booth toDomain(BoothEntity entity) {
        return Booth.builder()
            .id(entity.getId())
            .universityId(entity.getUniversity().getId())
            .name(entity.getName())
            .description(entity.getDescription())
            .capacity(entity.getCapacity())
            .openTime(entity.getOpenTime())
            .closeTime(entity.getCloseTime())
            .status(entity.getStatus())
            .build();
    }
}