package com.festin.app.waiting.adapter.out.notification;

import com.festin.app.common.config.NotificationQueueConfig;
import com.festin.app.waiting.application.port.out.NotificationPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ 알림 Adapter
 *
 * NotificationPort 구현체로, RabbitMQ에 알림 메시지를 발행합니다.
 * 실제 FCM 발송은 FcmNotificationConsumer가 처리합니다.
 */
@Slf4j
@Primary
@Component
@RequiredArgsConstructor
public class RabbitMqNotificationAdapter implements NotificationPort {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void send(NotificationCommand command) {
        try {
            rabbitTemplate.convertAndSend(
                    NotificationQueueConfig.NOTIFICATION_QUEUE,
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
                    log.info("RabbitMQ에 호출 알림 발행 - userId: {}, boothId: {}, position: {}",
                            notification.userId(),
                            notification.boothId(),
                            notification.calledPosition());
        }
    }

    private void logPublishError(NotificationCommand command, Exception e) {
        switch (command) {
            case CallNotification notification ->
                    log.error("RabbitMQ 메시지 발행 실패 - userId: {}, boothId: {}, error: {}",
                            notification.userId(),
                            notification.boothId(),
                            e.getMessage(),
                            e);
        }
    }
}