package com.festin.waiting.adapter.out.persistence;

import com.festin.waiting.adapter.out.persistence.entity.WaitingEntity;
import com.festin.waiting.adapter.out.persistence.mapper.WaitingMapper;
import com.festin.waiting.adapter.out.persistence.repository.WaitingJpaRepository;
import com.festin.waiting.application.port.out.WaitingRepositoryPort;
import com.festin.waiting.domain.model.Waiting;
import com.festin.waiting.domain.model.WaitingStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Waiting JPA Adapter
 *
 * WaitingRepositoryPort 구현체
 * JPA를 사용한 MySQL 접근
 */
@Component
@RequiredArgsConstructor
public class JpaWaitingAdapter implements WaitingRepositoryPort {

    private final WaitingJpaRepository waitingJpaRepository;
    private final WaitingMapper waitingMapper;

    @Override
    public Waiting save(Waiting waiting) {
        // Domain → Entity 변환은 Mapper에서 처리
        // 현재는 호출(CALLED) 시점에만 저장하므로, 이 메서드는 사용되지 않음
        throw new UnsupportedOperationException("Waiting은 호출 시점에만 저장됩니다.");
    }

    @Override
    public Optional<Waiting> findById(Long waitingId) {
        return waitingJpaRepository.findById(waitingId)
            .map(waitingMapper::toDomain);
    }

    @Override
    public int countActiveByUserId(Long userId, List<WaitingStatus> statuses) {
        return waitingJpaRepository.countByUserIdAndStatusIn(userId, statuses);
    }
}
