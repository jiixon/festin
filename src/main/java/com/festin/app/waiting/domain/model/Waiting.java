package com.festin.app.waiting.domain.model;

import com.festin.app.waiting.application.port.in.result.CompleteResult;
import com.festin.app.waiting.domain.exception.InvalidStatusException;
import com.festin.app.waiting.domain.exception.WaitingNotFoundException;

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

    // private 생성자: 정적 팩토리 메서드를 통해서만 객체 생성 가능
    private Waiting(
            Long id,
            Long userId,
            Long boothId,
            Integer calledPosition,
            LocalDateTime registeredAt,
            LocalDateTime calledAt,
            WaitingStatus status,
            CompletionType completionType,
            LocalDateTime enteredAt,
            LocalDateTime completedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.boothId = boothId;
        this.calledPosition = calledPosition;
        this.registeredAt = registeredAt;
        this.calledAt = calledAt;
        this.status = status;
        this.completionType = completionType;
        this.enteredAt = enteredAt;
        this.completedAt = completedAt;
    }

    /**
     * Waiting 생성
     *
     * 용도:
     * - WaitingMapper에서 Entity → Domain 변환 시 사용
     * - 모든 필드를 포함하여 완전한 Waiting 객체 생성
     *
     * @param id Waiting ID
     * @param userId 사용자 ID
     * @param boothId 부스 ID
     * @param calledPosition 호출 순번
     * @param registeredAt 등록 시간
     * @param calledAt 호출 시간
     * @param status 상태
     * @param completionType 완료 유형
     * @param enteredAt 입장 시간
     * @param completedAt 완료 시간
     * @return Waiting 도메인 객체
     */
    public static Waiting of(
            Long id,
            Long userId,
            Long boothId,
            Integer calledPosition,
            LocalDateTime registeredAt,
            LocalDateTime calledAt,
            WaitingStatus status,
            CompletionType completionType,
            LocalDateTime enteredAt,
            LocalDateTime completedAt
    ) {
        return new Waiting(
                id,
                userId,
                boothId,
                calledPosition,
                registeredAt,
                calledAt,
                status,
                completionType,
                enteredAt,
                completedAt
        );
    }

    /**
     * 호출된 Waiting 생성
     *
     * 비즈니스 의미:
     * - 대기열에서 호출되어 MySQL에 저장할 Waiting 생성
     * - 상태는 항상 CALLED
     * - ID는 null (영속화 시점에 생성됨)
     *
     * 용도:
     * - CallNextService에서 새로운 호출 시 사용
     *
     * @param userId 사용자 ID
     * @param boothId 부스 ID
     * @param calledPosition 호출 순번
     * @param registeredAt 등록 시간 (Redis에서 가져온 원본 등록 시간)
     * @param calledAt 호출 시간
     * @return CALLED 상태의 Waiting 도메인 객체
     */
    public static Waiting ofCalled(
            Long userId,
            Long boothId,
            Integer calledPosition,
            LocalDateTime registeredAt,
            LocalDateTime calledAt
    ) {
        return new Waiting(
                null,  // id는 영속화 시점에 생성됨
                userId,
                boothId,
                calledPosition,
                registeredAt,
                calledAt,
                WaitingStatus.CALLED,
                null,  // completionType은 나중에 설정
                null,  // enteredAt은 나중에 설정
                null   // completedAt은 나중에 설정
        );
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
}