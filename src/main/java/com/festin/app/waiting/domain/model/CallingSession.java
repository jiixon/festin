package com.festin.app.waiting.domain.model;

import com.festin.app.waiting.application.port.in.result.CallResult;
import com.festin.app.waiting.application.port.out.NotificationPort;
import com.festin.app.waiting.application.port.out.QueueCachePort;

import java.time.LocalDateTime;

/**
 * 호출 세션 (Calling Session) - Value Object
 *
 * 책임:
 * - 다음 사람 호출 프로세스의 컨텍스트 표현
 * - 호출 관련 데이터 캡슐화
 * - Waiting, Notification, CallResult 변환 로직 제공
 *
 * 불변 객체 (Immutable)
 */
public class CallingSession {

    private final Long boothId;
    private final Long userId;
    private final LocalDateTime registeredAt;
    private final LocalDateTime calledAt;

    private CallingSession(Long boothId, Long userId, LocalDateTime registeredAt, LocalDateTime calledAt) {
        this.boothId = boothId;
        this.userId = userId;
        this.registeredAt = registeredAt;
        this.calledAt = calledAt;
    }

    public static CallingSession from(Long boothId, QueueCachePort.QueueItem queueItem) {
        return new CallingSession(
                boothId,
                queueItem.userId(),
                queueItem.registeredAt(),
                LocalDateTime.now()
        );
    }

    public Waiting toWaiting() {
        return Waiting.ofCalled(userId, boothId, 1, registeredAt, calledAt);
    }

    public NotificationPort.CallNotification toNotification(Long waitingId, String boothName) {
        return new NotificationPort.CallNotification(
                "call:" + waitingId,
                userId,
                boothId,
                boothName,
                1
        );
    }

    public CallResult toResult(Long waitingId) {
        return new CallResult(waitingId, userId, 1, calledAt);
    }

    public Long getBoothId() {
        return boothId;
    }

    public Long getUserId() {
        return userId;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }
}