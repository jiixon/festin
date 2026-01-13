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
import com.festin.app.waiting.application.port.out.dto.CalledWaitingInfo;
import com.festin.app.waiting.domain.model.Waiting;
import com.festin.app.waiting.domain.model.WaitingStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
     * Waiting Aggregate 저장/업데이트
     *
     * DDD 관점:
     * - Waiting Aggregate는 userId, boothId로 다른 Aggregate 참조 (올바른 설계)
     * - Adapter(Infrastructure)에서 JPA 연관관계를 위한 Entity 참조만 처리
     * - Domain은 Infrastructure 세부사항을 알지 못함 (Hexagonal Architecture 유지)
     *
     * JPA 동작:
     * - Entity에 ID가 null이면 INSERT
     * - Entity에 ID가 있으면 UPDATE (merge)
     * - getReference()로 프록시 생성 (실제 DB 조회 없음)
     */
    @Override
    public Waiting save(Waiting waiting) {
        // JPA 연관관계를 위한 Entity 참조 (프록시 사용, DB 조회 X)
        // Application 계층에서 이미 검증된 ID라고 신뢰
        UserEntity user = userJpaRepository.getReferenceById(waiting.getUserId());
        BoothEntity booth = boothJpaRepository.getReferenceById(waiting.getBoothId());

        // Domain → Entity 변환 (ID 포함)
        WaitingEntity entity = waitingMapper.toEntity(waiting, user, booth);

        // 영속화 (JPA가 ID 유무로 INSERT/UPDATE 판단)
        WaitingEntity savedEntity = waitingJpaRepository.save(entity);

        // Entity → Domain 변환하여 반환
        return waitingMapper.toDomain(savedEntity);
    }

    @Override
    public Optional<Waiting> findById(Long waitingId) {
        return waitingJpaRepository.findById(waitingId)
                .map(waitingMapper::toDomain);
    }

    @Override
    public List<Waiting> findActiveWaitingsByUserId(Long userId) {
        return waitingJpaRepository.findByUserIdAndStatus(userId, WaitingStatus.CALLED).stream()
                .map(waitingMapper::toDomain)
                .toList();
    }

    @Override
    public int countTodayCalledByBoothId(Long boothId, LocalDate date) {
        return waitingJpaRepository.countTodayCalledByBoothId(boothId, date);
    }

    @Override
    public int countTodayEnteredByBoothId(Long boothId, LocalDate date) {
        return waitingJpaRepository.countTodayEnteredByBoothId(boothId, date);
    }

    @Override
    public int countTodayNoShowByBoothId(Long boothId, LocalDate date) {
        return waitingJpaRepository.countTodayNoShowByBoothId(boothId, date);
    }

    @Override
    public int countTodayCompletedByBoothId(Long boothId, LocalDate date) {
        return waitingJpaRepository.countTodayCompletedByBoothId(boothId, date);
    }

    @Override
    public List<CalledWaitingInfo> findCalledByBoothIdWithUserInfo(Long boothId) {
        return waitingJpaRepository.findByBoothIdAndStatusesWithUser(boothId, WaitingStatus.ACTIVE_STATUSES)
                .stream()
                .map(entity -> new CalledWaitingInfo(
                        entity.getId(),
                        entity.getUser().getId(),
                        entity.getUser().getNickname(),
                        entity.getBooth().getId(),
                        entity.getCalledPosition(),
                        entity.getStatus(),
                        entity.getCalledAt(),
                        entity.getEnteredAt()))
                .toList();
    }

    @Override
    public List<Waiting> findTimeoutWaitings(LocalDateTime timeoutThreshold) {
        return waitingJpaRepository.findByStatusAndCalledAtBefore(WaitingStatus.CALLED, timeoutThreshold)
                .stream()
                .map(waitingMapper::toDomain)
                .toList();
    }

    @Override
    public List<Waiting> findRecentByStatus(WaitingStatus status, LocalDateTime since) {
        return waitingJpaRepository.findByStatusAndCalledAtAfter(status, since)
                .stream()
                .map(waitingMapper::toDomain)
                .toList();
    }

    @Override
    public boolean existsByUserIdAndBoothIdAndStatus(Long userId, Long boothId, WaitingStatus status) {
        return waitingJpaRepository.existsByUserIdAndBoothIdAndStatus(userId, boothId, status);
    }
}
