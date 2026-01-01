package com.festin.app.fixture;

import com.festin.app.user.adapter.out.persistence.entity.UserEntity;
import com.festin.app.user.adapter.out.persistence.repository.UserJpaRepository;
import com.festin.app.user.domain.model.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * User Fixture
 *
 * 테스트용 User 엔티티 생성 헬퍼
 *
 * 책임:
 * - User Entity 생성 및 저장
 * - 이메일 중복 방지 (타임스탬프 활용)
 * - FCM 토큰 설정
 */
@Component
public class UserFixture {

    @Autowired
    private UserJpaRepository userRepository;

    /**
     * VISITOR Role 사용자 생성
     *
     * @param nickname 닉네임
     * @return 생성된 사용자 ID
     */
    public Long createVisitor(String nickname) {
        String email = nickname + "-" + System.currentTimeMillis() + "@test.com";
        UserEntity user = new UserEntity(email, nickname, Role.VISITOR);
        return userRepository.save(user).getId();
    }

    /**
     * FCM 토큰을 가진 VISITOR 사용자 생성
     *
     * @param nickname 닉네임
     * @param fcmToken FCM 토큰
     * @return 생성된 사용자 ID
     */
    public Long createVisitorWithFcm(String nickname, String fcmToken) {
        Long userId = createVisitor(nickname);
        UserEntity user = userRepository.findById(userId).orElseThrow();
        user.updateFcmToken(fcmToken);
        return userRepository.save(user).getId();
    }

    /**
     * STAFF Role 사용자 생성
     *
     * @param nickname 닉네임
     * @return 생성된 사용자 ID
     */
    public Long createStaff(String nickname) {
        String email = nickname + "-" + System.currentTimeMillis() + "@test.com";
        UserEntity user = new UserEntity(email, nickname, Role.STAFF);
        return userRepository.save(user).getId();
    }
}
