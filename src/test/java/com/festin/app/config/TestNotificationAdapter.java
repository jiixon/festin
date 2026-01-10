package com.festin.app.config;

import com.festin.app.waiting.application.port.out.NotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 테스트용 Notification Adapter
 *
 * 테스트 전용 큐로 메시지를 발송하여 festin-app과의 충돌 방지
 */
@Primary
@Component
public class TestNotificationAdapter implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(TestNotificationAdapter.class);

    private final RabbitTemplate rabbitTemplate;

    public TestNotificationAdapter(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void send(NotificationCommand command) {
        try {
            rabbitTemplate.convertAndSend(
                    TestRabbitMQConfig.TEST_NOTIFICATION_QUEUE,
                    command
            );

            logPublished(command);

        } catch (Exception e) {
            logPublishError(command, e);
        }
    }

    private void logPublished(NotificationCommand command) {
        switch (command) {
            case CallNotification notification ->
                    log.info("[테스트] RabbitMQ에 호출 알림 발행 - userId: {}, boothId: {}, position: {}",
                            notification.userId(),
                            notification.boothId(),
                            notification.calledPosition());
        }
    }

    private void logPublishError(NotificationCommand command, Exception e) {
        switch (command) {
            case CallNotification notification ->
                    log.error("[테스트] RabbitMQ 메시지 발행 실패 - userId: {}, boothId: {}, error: {}",
                            notification.userId(),
                            notification.boothId(),
                            e.getMessage(),
                            e);
        }
    }
}