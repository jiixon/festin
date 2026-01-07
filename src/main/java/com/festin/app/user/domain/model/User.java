package com.festin.app.user.domain.model;

import com.festin.app.user.domain.InvalidFcmTokenException;
import com.festin.app.user.domain.NotificationDisabledException;

/**
 * User (사용자) - 도메인 모델
 *
 * 책임:
 * - 사용자 정보 관리
 * - 알림 설정 관리
 */
public class User {

    private final Long id;
    private final String email;
    private final String nickname;
    private final Role role;
    private final String fcmToken;
    private final Boolean notificationEnabled;

    private User(Long id, String email, String nickname, Role role, String fcmToken, Boolean notificationEnabled) {
        this.id = id;
        this.email = email;
        this.nickname = nickname;
        this.role = role;
        this.fcmToken = fcmToken;
        this.notificationEnabled = notificationEnabled;
    }

    public static User of(Long id, String email, String nickname, Role role) {
        return new User(id, email, nickname, role, null, true);
    }

    public static User of(Long id, String email, String nickname, Role role, String fcmToken, Boolean notificationEnabled) {
        return new User(id, email, nickname, role, fcmToken, notificationEnabled);
    }

    /**
     * 알림 수신 가능 여부
     */
    public boolean canReceiveNotification() {
        return notificationEnabled && fcmToken != null && !fcmToken.isEmpty();
    }

    /**
     * 디바이스 등록 (FCM 푸시 알림용)
     *
     * 비즈니스 규칙:
     * - 알림이 활성화된 사용자만 디바이스 등록 가능
     * - FCM 토큰은 필수이며 빈 문자열 불가
     *
     * @param fcmToken FCM 토큰
     * @return 디바이스가 등록된 새로운 User 객체
     * @throws NotificationDisabledException 알림이 비활성화된 경우
     * @throws InvalidFcmTokenException FCM 토큰이 유효하지 않은 경우
     */
    public User registerDeviceForNotification(String fcmToken) {
        validateNotificationEnabled();
        validateFcmToken(fcmToken);

        return new User(
                this.id,
                this.email,
                this.nickname,
                this.role,
                fcmToken,
                this.notificationEnabled
        );
    }

    private void validateNotificationEnabled() {
        if (!this.notificationEnabled) {
            throw new NotificationDisabledException();
        }
    }

    private void validateFcmToken(String fcmToken) {
        if (fcmToken == null || fcmToken.isBlank()) {
            throw new InvalidFcmTokenException();
        }
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getNickname() {
        return nickname;
    }

    public Role getRole() {
        return role;
    }

    public String getFcmToken() {
        return fcmToken;
    }

    public Boolean getNotificationEnabled() {
        return notificationEnabled;
    }
}
