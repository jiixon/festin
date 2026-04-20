package com.festin.app.booth.adapter.out.persistence;

import com.festin.app.booth.adapter.out.persistence.entity.BoothEntity;
import com.festin.app.booth.adapter.out.persistence.mapper.BoothMapper;
import com.festin.app.booth.adapter.out.persistence.repository.BoothJpaRepository;
import com.festin.app.booth.application.port.out.BoothRepositoryPort;
import com.festin.app.booth.application.port.out.dto.BoothInfo;
import com.festin.app.booth.domain.model.Booth;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
    public List<Booth> findAll() {
        return boothJpaRepository.findAll().stream()
            .map(boothMapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<Booth> findAllByUniversityId(Long universityId) {
        return boothJpaRepository.findAllByUniversityIdWithUniversity(universityId).stream()
            .map(boothMapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<BoothInfo> findAllBoothInfo() {
        return boothJpaRepository.findAllWithUniversity().stream()
            .map(this::toBoothInfo)
            .collect(Collectors.toList());
    }

    @Override
    public List<BoothInfo> findAllOpenBoothInfo() {
        return boothJpaRepository.findAllOpenWithUniversity().stream()
            .map(this::toBoothInfo)
            .collect(Collectors.toList());
    }

    @Override
    public List<BoothInfo> findAllBoothInfoByUniversityId(Long universityId) {
        return boothJpaRepository.findAllByUniversityIdWithUniversity(universityId).stream()
            .map(this::toBoothInfo)
            .collect(Collectors.toList());
    }

    @Override
    public Optional<BoothInfo> findBoothInfoById(Long boothId) {
        return boothJpaRepository.findById(boothId)
            .map(entity -> {
                // University를 명시적으로 로드
                entity.getUniversity().getName();
                return toBoothInfo(entity);
            });
    }

    private BoothInfo toBoothInfo(BoothEntity entity) {
        return new BoothInfo(
            entity.getId(),
            entity.getName(),
            entity.getDescription(),
            entity.getUniversity().getId(),
            entity.getUniversity().getName(),
            entity.getCapacity(),
            entity.getStatus(),
            entity.getOpenTime(),
            entity.getCloseTime()
        );
    }
}
