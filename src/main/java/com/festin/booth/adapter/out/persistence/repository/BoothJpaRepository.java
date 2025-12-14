package com.festin.app.adapter.out.persistence.repository;

import com.festin.app.adapter.out.persistence.entity.BoothEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Booth JPA Repository
 *
 * Spring Data JPA 인터페이스
 */
public interface BoothJpaRepository extends JpaRepository<BoothEntity, Long> {
}