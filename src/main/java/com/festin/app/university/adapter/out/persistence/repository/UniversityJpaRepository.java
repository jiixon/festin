package com.festin.university.adapter.out.persistence.repository;

import com.festin.university.adapter.out.persistence.entity.UniversityEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UniversityJpaRepository extends JpaRepository<UniversityEntity, Long> {
}