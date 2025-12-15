package com.festin.user.adapter.out.persistence.repository;

import com.festin.user.adapter.out.persistence.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * User JPA Repository
 *
 * Spring Data JPA 인터페이스
 */
public interface UserJpaRepository extends JpaRepository<UserEntity, Long> {
}