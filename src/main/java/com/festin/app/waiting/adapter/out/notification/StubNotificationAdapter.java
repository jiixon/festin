package com.festin.app.waiting.adapter.out.notification;

import com.festin.app.waiting.application.port.out.NotificationPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 알림 발송 Stub Adapter
 *
 * TODO: RabbitMQ 또는 Kafka로 실제 구현 대체 예정
 *
 * 현재는 로그만 출력하는 Stub
 */
@Slf4j
@Component
public class StubNotificationAdapter implements NotificationPort {

    @Override
    public void sendCallNotification(CallNotification notification) {
        log.info("[STUB] 호출 알림 발송 - userId: {}, boothId: {}, boothName: {}, position: {}",
                notification.userId(),
                notification.boothId(),
                notification.boothName(),
                notification.calledPosition()
        );

        // TODO: 실제 알림 발송 로직
        // 1. RabbitMQ/Kafka로 메시지 발행
        // 2. FCM/APNs로 푸시 알림 전송
        // 3. 재시도 로직 (최대 3회)
    }
}