package com.festin.app.adapter.out.persistence;

import com.festin.app.adapter.out.persistence.mapper.BoothMapper;
import com.festin.app.adapter.out.persistence.repository.BoothJpaRepository;
import com.festin.app.application.port.out.BoothRepositoryPort;
import com.festin.app.domain.model.Booth;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Booth JPA Adapter
 *
 * BoothRepositoryPort 구현체
 * JPA를 사용한 MySQL 접근
 */
@Component
@RequiredArgsConstructor
public class JpaBoothAdapter implements BoothRepositoryPort {

    private final BoothJpaRepository boothJpaRepository;
    private final BoothMapper boothMapper;

    @Override
    public Optional<Booth> findById(Long boothId) {
        return boothJpaRepository.findById(boothId)
            .map(boothMapper::toDomain);
    }

    @Override
    public Booth save(Booth booth) {
        // 부스 저장은 관리자 기능에서 사용 (현재 UseCase에서는 미사용)
        throw new UnsupportedOperationException("부스 저장은 관리자 기능에서만 사용됩니다.");
    }
}
