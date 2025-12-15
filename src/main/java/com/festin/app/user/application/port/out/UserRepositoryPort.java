package com.festin.app.user.application.port.out;

import com.festin.app.user.domain.model.User;

import java.util.Optional;

/**
 * User 영구 저장소 Port
 *
 * 책임:
 * - 사용자 정보 조회
 * - FCM 토큰 관리
 *
 * 구현체:
 * - JpaUserAdapter (MySQL 기반)
 */
public interface UserRepositoryPort {

    /**
     * ID로 User 조회
     *
     * @param userId 사용자 ID
     * @return User 정보
     */
    Optional<User> findById(Long userId);

    /**
     * User 저장
     *
     * @param user 저장할 User
     * @return 저장된 User
     */
    User save(User user);
}
