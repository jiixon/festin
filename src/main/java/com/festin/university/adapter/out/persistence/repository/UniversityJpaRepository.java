package com.festin.app.adapter.out.persistence.repository;

import com.festin.app.adapter.out.persistence.entity.UniversityEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UniversityJpaRepository extends JpaRepository<UniversityEntity, Long> {
}