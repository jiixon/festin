package com.festin.app.adapter.out.persistence.entity;

import com.festin.app.domain.model.CompletionType;
import com.festin.app.domain.model.WaitingStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Waiting JPA Entity
 *
 * Domain과 분리된 Infrastructure 계층의 Entity
 * 호출(CALLED) 시점부터 MySQL에 저장
 */
@Entity
@Table(name = "waiting")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WaitingEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booth_id", nullable = false)
    private BoothEntity booth;

    @Column(name = "called_position", nullable = false)
    private Integer calledPosition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WaitingStatus status = WaitingStatus.CALLED;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CompletionType completionType;

    @Column(nullable = false)
    private LocalDateTime registeredAt;

    @Column(nullable = false)
    private LocalDateTime calledAt;

    private LocalDateTime enteredAt;

    private LocalDateTime completedAt;

    @Column(nullable = false)
    private Boolean notificationSent = false;

    @Column(nullable = false)
    private Integer notificationRetryCount = 0;

    public WaitingEntity(UserEntity user, BoothEntity booth, Integer calledPosition, LocalDateTime registeredAt, LocalDateTime calledAt) {
        this.user = user;
        this.booth = booth;
        this.calledPosition = calledPosition;
        this.registeredAt = registeredAt;
        this.calledAt = calledAt;
    }

    public void updateStatus(WaitingStatus status) {
        this.status = status;
    }

    public void updateCompletionType(CompletionType completionType) {
        this.completionType = completionType;
    }

    public void updateEnteredAt(LocalDateTime enteredAt) {
        this.enteredAt = enteredAt;
    }

    public void updateCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public void markNotificationSent() {
        this.notificationSent = true;
    }

    public void incrementRetryCount() {
        this.notificationRetryCount++;
    }
}