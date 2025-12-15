package com.festin.app.university.adapter.out.persistence.repository;

import com.festin.app.university.adapter.out.persistence.entity.UniversityEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UniversityJpaRepository extends JpaRepository<UniversityEntity, Long> {
}