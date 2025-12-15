package com.festin.waiting.adapter.out.persistence.mapper;

import com.festin.booth.adapter.out.persistence.entity.BoothEntity;
import com.festin.user.adapter.out.persistence.entity.UserEntity;
import com.festin.waiting.adapter.out.persistence.entity.WaitingEntity;
import com.festin.waiting.domain.model.Waiting;
import org.springframework.stereotype.Component;

/**
 * Waiting Mapper
 *
 * Entity ↔ Domain 변환
 */
@Component
public class WaitingMapper {

    /**
     * Entity → Domain
     */
    public Waiting toDomain(WaitingEntity entity) {
        return Waiting.builder()
            .id(entity.getId())
            .userId(entity.getUser().getId())
            .boothId(entity.getBooth().getId())
            .calledPosition(entity.getCalledPosition())
            .status(entity.getStatus())
            .completionType(entity.getCompletionType())
            .registeredAt(entity.getRegisteredAt())
            .calledAt(entity.getCalledAt())
            .enteredAt(entity.getEnteredAt())
            .completedAt(entity.getCompletedAt())
            .build();
    }

    /**
     * Domain → Entity
     *
     * @param domain Domain 모델
     * @param user UserEntity (연관관계)
     * @param booth BoothEntity (연관관계)
     */
    public WaitingEntity toEntity(Waiting domain, UserEntity user, BoothEntity booth) {
        return new WaitingEntity(
            user,
            booth,
            domain.getCalledPosition(),
            domain.getRegisteredAt(),
            domain.getCalledAt()
        );
    }
}