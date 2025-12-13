package com.festin.app.application.port.out;

/**
 * 알림 발송 Port
 *
 * 책임:
 * - Kafka를 통한 비동기 알림 발행
 * - 알림 유실 방지 및 재시도 보장
 *
 * 구현체:
 * - KafkaNotificationAdapter
 *
 * Kafka Topic:
 * - booth-call-notifications (호출 알림)
 */
public interface NotificationPort {

    /**
     * 호출 알림 발행
     *
     * @param notification 호출 알림 정보
     */
    void sendCallNotification(CallNotification notification);

    /**
     * 호출 알림 정보
     *
     * @param userId 사용자 ID
     * @param boothId 부스 ID
     * @param boothName 부스 이름
     * @param calledPosition 호출 순번
     */
    record CallNotification(
        Long userId,
        Long boothId,
        String boothName,
        Integer calledPosition
    ) {}
}