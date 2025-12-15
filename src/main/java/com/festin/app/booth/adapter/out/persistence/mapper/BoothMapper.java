package com.festin.app.booth.adapter.out.persistence.mapper;

import com.festin.app.booth.adapter.out.persistence.entity.BoothEntity;
import com.festin.app.booth.domain.model.Booth;
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