package com.festin.app.booth.adapter.out.persistence.repository;

import com.festin.app.booth.adapter.out.persistence.entity.BoothEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * Booth JPA Repository
 *
 * Spring Data JPA 인터페이스
 */
public interface BoothJpaRepository extends JpaRepository<BoothEntity, Long> {

    /**
     * University ID로 Booth 조회 (University fetch join)
     */
    @Query("SELECT b FROM BoothEntity b JOIN FETCH b.university WHERE b.university.id = :universityId")
    List<BoothEntity> findAllByUniversityIdWithUniversity(Long universityId);

    /**
     * 모든 Booth 조회 (University fetch join)
     */
    @Query("SELECT b FROM BoothEntity b JOIN FETCH b.university")
    List<BoothEntity> findAllWithUniversity();
}