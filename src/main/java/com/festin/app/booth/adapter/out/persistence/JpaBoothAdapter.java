package com.festin.app.booth.adapter.out.persistence;

import com.festin.app.booth.adapter.out.persistence.mapper.BoothMapper;
import com.festin.app.booth.adapter.out.persistence.repository.BoothJpaRepository;
import com.festin.app.booth.application.port.out.BoothRepositoryPort;
import com.festin.app.booth.domain.model.Booth;
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
}
