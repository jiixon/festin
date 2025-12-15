package com.festin.booth.adapter.out.persistence.mapper;

import com.festin.booth.adapter.out.persistence.entity.BoothEntity;
import com.festin.booth.domain.model.Booth;
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
        return Booth.of(
                        entity.getId(),
                        entity.getName(),
                        entity.getCapacity(),
                        entity.getStatus()
        );
    }
}