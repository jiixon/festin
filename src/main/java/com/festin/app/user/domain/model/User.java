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
    private final String fcmToken;
    private final Boolean notificationEnabled;

    private User(Builder builder) {
        this.id = builder.id;
        this.email = builder.email;
        this.nickname = builder.nickname;
        this.fcmToken = builder.fcmToken;
        this.notificationEnabled = builder.notificationEnabled;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 알림 수신 가능 여부
     */
    public boolean canReceiveNotification() {
        return notificationEnabled && fcmToken != null && !fcmToken.isEmpty();
    }

    // Getters
    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getNickname() {
        return nickname;
    }

    public String getFcmToken() {
        return fcmToken;
    }

    public Boolean getNotificationEnabled() {
        return notificationEnabled;
    }

    public static class Builder {
        private Long id;
        private String email;
        private String nickname;
        private String fcmToken;
        private Boolean notificationEnabled = true;

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder nickname(String nickname) {
            this.nickname = nickname;
            return this;
        }

        public Builder fcmToken(String fcmToken) {
            this.fcmToken = fcmToken;
            return this;
        }

        public Builder notificationEnabled(Boolean notificationEnabled) {
            this.notificationEnabled = notificationEnabled;
            return this;
        }

        public User build() {
            return new User(this);
        }
    }
}
