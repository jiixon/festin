package com.festin.app.waiting.adapter.out.persistence.mapper;

import com.festin.app.booth.adapter.out.persistence.entity.BoothEntity;
import com.festin.app.user.adapter.out.persistence.entity.UserEntity;
import com.festin.app.waiting.adapter.out.persistence.entity.WaitingEntity;
import com.festin.app.waiting.domain.model.Waiting;
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
        return Waiting.of(
                entity.getId(),
                entity.getUser().getId(),
                entity.getBooth().getId(),
                entity.getCalledPosition(),
                entity.getRegisteredAt(),
                entity.getCalledAt(),
                entity.getStatus(),
                entity.getCompletionType(),
                entity.getEnteredAt(),
                entity.getCompletedAt()
        );
    }

    /**
     * Domain → Entity
     *
     * @param domain Domain 모델
     * @param user UserEntity (연관관계)
     * @param booth BoothEntity (연관관계)
     * @return WaitingEntity (ID 포함)
     *
     * JPA가 ID 유무로 INSERT/UPDATE 자동 판단:
     * - ID가 null → INSERT
     * - ID가 있음 → UPDATE (merge)
     */
    public WaitingEntity toEntity(Waiting domain, UserEntity user, BoothEntity booth) {
        return new WaitingEntity(
            domain.getId(),  // ID 포함 (null이면 INSERT, 있으면 UPDATE)
            user,
            booth,
            domain.getCalledPosition(),
            domain.getStatus(),
            domain.getCompletionType(),
            domain.getRegisteredAt(),
            domain.getCalledAt(),
            domain.getEnteredAt(),
            domain.getCompletedAt()
        );
    }
}