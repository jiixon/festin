package com.festin.app.waiting.adapter.out.persistence;

import com.festin.app.booth.adapter.out.persistence.entity.BoothEntity;
import com.festin.app.booth.adapter.out.persistence.repository.BoothJpaRepository;
import com.festin.app.booth.domain.BoothNotFoundException;
import com.festin.app.user.adapter.out.persistence.entity.UserEntity;
import com.festin.app.user.adapter.out.persistence.repository.UserJpaRepository;
import com.festin.app.waiting.adapter.out.persistence.entity.WaitingEntity;
import com.festin.app.waiting.adapter.out.persistence.mapper.WaitingMapper;
import com.festin.app.waiting.adapter.out.persistence.repository.WaitingJpaRepository;
import com.festin.app.waiting.application.port.out.WaitingRepositoryPort;
import com.festin.app.waiting.domain.model.Waiting;
import com.festin.app.waiting.domain.model.WaitingStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Waiting JPA Adapter
 *
 * WaitingRepositoryPort 구현체
 * JPA를 사용한 MySQL 접근
 *
 * 책임:
 * - Waiting Aggregate의 영속화
 * - JPA 연관관계를 위한 Entity 조회 (Infrastructure 계층의 책임)
 * - Domain ↔ Entity 변환
 */
@Component
@RequiredArgsConstructor
public class JpaWaitingAdapter implements WaitingRepositoryPort {

    private final WaitingJpaRepository waitingJpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final BoothJpaRepository boothJpaRepository;
    private final WaitingMapper waitingMapper;

    /**
     * Waiting Aggregate 저장
     *
     * DDD 관점:
     * - Waiting Aggregate는 userId, boothId로 다른 Aggregate 참조 (올바른 설계)
     * - Adapter(Infrastructure)에서 JPA 연관관계를 위해 UserEntity, BoothEntity 조회
     * - Domain은 Infrastructure 세부사항을 알지 못함 (Hexagonal Architecture 유지)
     */
    @Override
    public Waiting save(Waiting waiting) {
        // JPA 연관관계를 위해 UserEntity, BoothEntity 조회
        // 이는 Infrastructure 계층의 책임 (Domain은 알 필요 없음)
        UserEntity user = userJpaRepository.findById(waiting.getUserId())
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + waiting.getUserId()));
        BoothEntity booth = boothJpaRepository.findById(waiting.getBoothId())
            .orElseThrow(BoothNotFoundException::new);

        // Domain → Entity 변환
        WaitingEntity entity = waitingMapper.toEntity(waiting, user, booth);

        // 영속화
        WaitingEntity savedEntity = waitingJpaRepository.save(entity);

        // Entity → Domain 변환하여 반환 (ID 포함)
        return waitingMapper.toDomain(savedEntity);
    }

    @Override
    public Optional<Waiting> findById(Long waitingId) {
        return waitingJpaRepository.findById(waitingId)
            .map(waitingMapper::toDomain);
    }
}
