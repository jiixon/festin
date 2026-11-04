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
     * Email로 User 조회
     *
     * @param email 사용자 이메일
     * @return User 정보
     */
    Optional<User> findByEmail(String email);

    /**
     * User 존재 여부 확인
     *
     * 대기열(Redis)은 사용자(MySQL)와 참조 무결성이 보장되지 않으므로,
     * 대기열에서 꺼낸 userId가 실재하는지 확인할 때 사용한다.
     * 도메인 객체 전체를 로드하지 않는 존재 확인 전용 연산이다.
     *
     * @param userId 사용자 ID
     * @return 존재하면 true
     */
    boolean existsById(Long userId);

    /**
     * User 저장
     *
     * @param user 저장할 User
     * @return 저장된 User
     */
    User save(User user);
}
