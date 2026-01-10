package com.festin.app.user.adapter.out.persistence.entity;

import com.festin.app.common.BaseTimeEntity;
import com.festin.app.user.domain.model.Role;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "fcm_token")
    private String fcmToken;

    @Column(name = "notification_enabled", nullable = false)
    private Boolean notificationEnabled = true;

    @Column(name = "managed_booth_id")
    private Long managedBoothId;

    public UserEntity(String email, String nickname, Role role) {
        this.email = email;
        this.nickname = nickname;
        this.role = role;
    }

    public UserEntity(String email, String nickname, Role role, Long managedBoothId) {
        this.email = email;
        this.nickname = nickname;
        this.role = role;
        this.managedBoothId = managedBoothId;
    }

    public void updateFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public void updateManagedBoothId(Long managedBoothId) {
        this.managedBoothId = managedBoothId;
    }
}
