package com.festin.app.booth.application.port.out;

import com.festin.app.booth.application.port.out.dto.BoothInfo;
import com.festin.app.booth.domain.model.Booth;

import java.util.List;
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

    /**
     * 모든 Booth 조회
     *
     * @return Booth 목록
     */
    List<Booth> findAll();

    /**
     * 대학별 Booth 조회
     *
     * @param universityId 대학 ID
     * @return Booth 목록
     */
    List<Booth> findAllByUniversityId(Long universityId);

    /**
     * 모든 Booth 정보 조회 (Read Model)
     *
     * @return BoothInfo 목록
     */
    List<BoothInfo> findAllBoothInfo();

    /**
     * 대학별 Booth 정보 조회 (Read Model)
     *
     * @param universityId 대학 ID
     * @return BoothInfo 목록
     */
    List<BoothInfo> findAllBoothInfoByUniversityId(Long universityId);

}
