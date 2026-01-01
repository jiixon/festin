package com.festin.app.waiting.application.port.out;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * 알림 발송 Port
 *
 * 책임:
 * - RabbitMQ를 통한 비동기 알림 발행
 * - 알림 유실 방지 및 재시도 보장
 *
 * 구현체:
 * - RabbitMqNotificationAdapter
 *
 * RabbitMQ Queue:
 * - booth-call-notifications (호출 알림)
 */
public interface NotificationPort {

    /**
     * 알림 발송
     *
     * @param command 알림 명령
     */
    void send(NotificationCommand command);

    /**
     * 알림 명령 인터페이스
     *
     * 다양한 알림 타입을 지원하기 위한 sealed interface
     * Jackson 다형성 직렬화를 위해 @JsonTypeInfo와 @JsonSubTypes 사용
     *
     * - CallNotification: 호출 알림 (현재 구현)
     * - QueuePositionNotification: 순번 알림 (향후 추가)
     * - NoShowNotification: 노쇼 알림 (향후 추가)
     */
    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.PROPERTY,
            property = "type"
    )
    @JsonSubTypes({
            @JsonSubTypes.Type(value = CallNotification.class, name = "CALL")
    })
    sealed interface NotificationCommand permits CallNotification {
        /**
         * 이벤트 고유 ID (중복 방지용)
         *
         * @return 이벤트 ID
         */
        String eventId();
    }

    /**
     * 호출 알림 정보
     *
     * 스태프가 다음 사람을 호출했을 때 발송되는 알림
     *
     * @param eventId 이벤트 고유 ID (중복 방지용, 형식: "call:{waitingId}")
     * @param userId 사용자 ID
     * @param boothId 부스 ID
     * @param boothName 부스 이름
     * @param calledPosition 호출 순번
     */
    record CallNotification(
        String eventId,
        Long userId,
        Long boothId,
        String boothName,
        Integer calledPosition
    ) implements NotificationCommand {}
}