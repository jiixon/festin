package com.festin.app.booth.application.port.out;

import com.festin.app.booth.domain.model.Booth;

import java.util.Optional;

/**
 * Booth 영구 저장소 Port
 *
 * 책임:
 * - Booth 정보 조회
 * - Booth 운영 상태 업데이트
 *
 * 구현체:
 * - JpaBoothAdapter (MySQL 기반)
 */
public interface BoothRepositoryPort {

    /**
     * ID로 Booth 조회
     *
     * @param boothId 부스 ID
     * @return Booth 정보
     */
    Optional<Booth> findById(Long boothId);

}
