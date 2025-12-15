package com.festin.user.adapter.out.persistence.entity;
import com.festin.common.BaseTimeEntity;
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

    @Column(name = "fcm_token")
    private String fcmToken;

    @Column(name = "notification_enabled", nullable = false)
    private Boolean notificationEnabled = true;

    public UserEntity(String email, String nickname, String fcmToken) {
        this.email = email;
        this.nickname = nickname;
        this.fcmToken = fcmToken;
    }
}
