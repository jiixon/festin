package com.festin.app.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user")
@Getter
@NoArgsConstructor
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
}
