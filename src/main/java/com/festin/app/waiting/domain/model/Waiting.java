package com.festin.waiting.domain.model;

import com.festin.waiting.domain.exception.InvalidStatusException;

import java.time.LocalDateTime;

/**
 * Waiting (대기) - 핵심 도메인 모델
 *
 * 책임:
 * - 대기 상태 및 생명주기 관리
 * - 호출부터 완료까지 상태 전이 관리
 * - 노쇼, 취소 등 예외 상황 처리
 *
 * 불변 규칙 (Invariants):
 * - 호출 시각 >= 등록 시각
 * - 입장 시각 >= 호출 시각
 * - 완료 시각 >= 입장 시각
 * - 호출된 대기는 반드시 완료 유형을 가져야 함
 */
public class Waiting {

    private final Long id;
    private final Long userId;
    private final Long boothId;
    private final Integer calledPosition;      // 호출 순번
    private final LocalDateTime registeredAt;  // 대기 등록 시간
    private final LocalDateTime calledAt;      // 호출 시간

    private WaitingStatus status;
    private CompletionType completionType;
    private LocalDateTime enteredAt;           // 입장 확인 시간
    private LocalDateTime completedAt;         // 완료 시간

    // private 생성자: Builder를 통해서만 객체 생성 가능
    private Waiting(Builder builder) {
        this.id = builder.id;
        this.userId = builder.userId;
        this.boothId = builder.boothId;
        this.calledPosition = builder.calledPosition;
        this.registeredAt = builder.registeredAt;
        this.calledAt = builder.calledAt;
        this.status = builder.status;
        this.completionType = builder.completionType;
        this.enteredAt = builder.enteredAt;
        this.completedAt = builder.completedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 입장 확인
     *
     * 비즈니스 규칙:
     * - 현재 상태가 CALLED여야 함
     * - 상태를 ENTERED로 전이
     */
    public void enter() {
        if (this.status != WaitingStatus.CALLED) {
            throw InvalidStatusException.notCalled();
        }
        this.status = WaitingStatus.ENTERED;
        this.enteredAt = LocalDateTime.now();
    }

    /**
     * 체험 완료
     *
     * 비즈니스 규칙:
     * - 현재 상태가 ENTERED여야 함
     * - 상태를 COMPLETED로 전이
     * - 완료 유형을 ENTERED로 설정
     */
    public void complete() {
        if (this.status != WaitingStatus.ENTERED) {
            throw InvalidStatusException.notEntered();
        }
        this.status = WaitingStatus.COMPLETED;
        this.completionType = CompletionType.ENTERED;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * 노쇼 처리
     *
     * 비즈니스 규칙:
     * - 현재 상태가 CALLED여야 함
     * - 상태를 COMPLETED로 전이
     * - 완료 유형을 NO_SHOW로 설정
     */
    public void markAsNoShow() {
        if (this.status != WaitingStatus.CALLED) {
            throw InvalidStatusException.notCalled();
        }
        this.status = WaitingStatus.COMPLETED;
        this.completionType = CompletionType.NO_SHOW;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * 대기 취소
     *
     * 비즈니스 규칙:
     * - 현재 상태가 CALLED여야 함
     * - 상태를 COMPLETED로 전이
     * - 완료 유형을 CANCELLED로 설정
     */
    public void cancel() {
        if (this.status != WaitingStatus.CALLED) {
            throw InvalidStatusException.notCalled();
        }
        this.status = WaitingStatus.COMPLETED;
        this.completionType = CompletionType.CANCELLED;
        this.completedAt = LocalDateTime.now();
    }

    // Getters
    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getBoothId() {
        return boothId;
    }

    public Integer getCalledPosition() {
        return calledPosition;
    }

    public WaitingStatus getStatus() {
        return status;
    }

    public CompletionType getCompletionType() {
        return completionType;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }

    public LocalDateTime getCalledAt() {
        return calledAt;
    }

    public LocalDateTime getEnteredAt() {
        return enteredAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public static class Builder {
        private Long id;
        private Long userId;
        private Long boothId;
        private Integer calledPosition;
        private LocalDateTime registeredAt;
        private LocalDateTime calledAt;
        private WaitingStatus status = WaitingStatus.CALLED;
        private CompletionType completionType;
        private LocalDateTime enteredAt;
        private LocalDateTime completedAt;

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public Builder boothId(Long boothId) {
            this.boothId = boothId;
            return this;
        }

        public Builder calledPosition(Integer calledPosition) {
            this.calledPosition = calledPosition;
            return this;
        }

        public Builder registeredAt(LocalDateTime registeredAt) {
            this.registeredAt = registeredAt;
            return this;
        }

        public Builder calledAt(LocalDateTime calledAt) {
            this.calledAt = calledAt;
            return this;
        }

        public Builder status(WaitingStatus status) {
            this.status = status;
            return this;
        }

        public Builder completionType(CompletionType completionType) {
            this.completionType = completionType;
            return this;
        }

        public Builder enteredAt(LocalDateTime enteredAt) {
            this.enteredAt = enteredAt;
            return this;
        }

        public Builder completedAt(LocalDateTime completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public Waiting build() {
            return new Waiting(this);
        }
    }
}