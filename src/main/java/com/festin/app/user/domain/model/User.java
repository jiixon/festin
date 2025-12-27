package com.festin.app.user.domain.model;

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
