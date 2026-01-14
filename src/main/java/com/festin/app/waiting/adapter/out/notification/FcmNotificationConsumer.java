package com.festin.app.waiting.adapter.out.notification;

import com.festin.app.common.config.NotificationQueueConfig;
import com.festin.app.common.firebase.FirebaseClient;
import com.festin.app.user.application.port.out.FcmTokenCachePort;
import com.festin.app.waiting.application.port.out.NotificationIdempotencyPort;
import com.festin.app.waiting.application.port.out.NotificationPort.CallNotification;
import com.festin.app.waiting.application.port.out.NotificationPort.NotificationCommand;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

/**
 * FCM 푸시 알림 Consumer
 *
 * RabbitMQ에서 알림 메시지를 소비하고 FCM으로 실제 푸시 알림을 발송합니다.
 * - ACK/NACK 전략으로 메시지 유실 방지
 * - 중복 방지 메커니즘으로 동일 알림 중복 발송 방지
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!test")
public class FcmNotificationConsumer {

    private final FcmTokenCachePort fcmTokenCachePort;
    private final FirebaseClient firebaseClient;
    private final NotificationIdempotencyPort idempotencyPort;

    @RabbitListener(queues = NotificationQueueConfig.NOTIFICATION_QUEUE, ackMode = "MANUAL")
    public void handleNotification(
            NotificationCommand command,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        logReceived(command);

        // 중복 처리 체크
        if (!idempotencyPort.tryProcess(command.eventId())) {
            log.warn("이미 처리된 이벤트입니다 - eventId: {}", command.eventId());
            ack(channel, deliveryTag);
            return;
        }

        try {
            processNotification(command);
            idempotencyPort.markProcessed(command.eventId());
            ack(channel, deliveryTag);

        } catch (Exception e) {
            handleError(command, e, channel, deliveryTag);
        }
    }

    private void processNotification(NotificationCommand command) throws FirebaseMessagingException {
        Long userId = getUserId(command);
        Optional<String> fcmTokenOpt = fcmTokenCachePort.getFcmToken(userId);

        if (fcmTokenOpt.isEmpty()) {
            log.warn("FCM 토큰이 없는 사용자입니다 - userId: {}", userId);
            return;
        }

        String fcmToken = fcmTokenOpt.get();
        Message message = buildMessage(command, fcmToken);
        String response = firebaseClient.send(message);
        logSuccess(command, response);
    }

    private Long getUserId(NotificationCommand command) {
        return switch (command) {
            case CallNotification notification -> notification.userId();
        };
    }

    private Message buildMessage(NotificationCommand command, String fcmToken) {
        return switch (command) {
            case CallNotification notification -> buildCallMessage(notification, fcmToken);
        };
    }

    private Message buildCallMessage(CallNotification notification, String fcmToken) {
        return Message.builder()
                .setToken(fcmToken)
                // .setNotification(Notification.builder()
                // .setTitle("부스 호출 알림")
                // .setBody(String.format("%s 부스에서 %d번째로 호출되었습니다!",
                // notification.boothName(),
                // notification.calledPosition()))
                // .build())
                .putData("type", "CALL")
                .putData("title", "부스 호출 알림")
                .putData("body", String.format("%s 부스에서 %d번째로 호출되었습니다!",
                        notification.boothName(),
                        notification.calledPosition()))
                .putData("boothId", String.valueOf(notification.boothId()))
                .putData("calledPosition", String.valueOf(notification.calledPosition()))
                .build();
    }

    private void handleError(NotificationCommand command, Exception e, Channel channel, long deliveryTag) {
        if (e instanceof FirebaseMessagingException fme) {
            handleFcmError(command, fme);
            ack(channel, deliveryTag); // FCM 오류는 재시도 안 함
        } else {
            log.error("알림 처리 중 예외 발생 - eventId: {}, error: {}",
                    command.eventId(), e.getMessage(), e);
            nack(channel, deliveryTag); // 재시도 가능한 오류
        }
    }

    private void handleFcmError(NotificationCommand command, FirebaseMessagingException e) {
        String errorCode = String.valueOf(e.getErrorCode());
        Long userId = getUserId(command);

        // 잘못된 토큰인 경우 캐시에서 삭제
        if ("INVALID_ARGUMENT".equals(errorCode) ||
                "UNREGISTERED".equals(errorCode) ||
                "SENDER_ID_MISMATCH".equals(errorCode)) {

            log.warn("유효하지 않은 FCM 토큰 - userId: {}, errorCode: {}, 캐시에서 삭제합니다",
                    userId, errorCode);
            fcmTokenCachePort.deleteFcmToken(userId);

        } else {
            logError(command, errorCode, e);
        }
    }

    private void ack(Channel channel, long deliveryTag) {
        try {
            channel.basicAck(deliveryTag, false);
        } catch (IOException e) {
            log.error("ACK 실패 - deliveryTag: {}", deliveryTag, e);
        }
    }

    private void nack(Channel channel, long deliveryTag) {
        try {
            channel.basicNack(deliveryTag, false, true); // requeue = true
        } catch (IOException e) {
            log.error("NACK 실패 - deliveryTag: {}", deliveryTag, e);
        }
    }

    private void logReceived(NotificationCommand command) {
        switch (command) {
            case CallNotification notification ->
                log.info("호출 알림 수신 - eventId: {}, userId: {}, boothId: {}, position: {}",
                        notification.eventId(),
                        notification.userId(),
                        notification.boothId(),
                        notification.calledPosition());
        }
    }

    private void logSuccess(NotificationCommand command, String messageId) {
        switch (command) {
            case CallNotification notification ->
                log.info("FCM 푸시 알림 발송 성공 - eventId: {}, userId: {}, boothId: {}, messageId: {}",
                        notification.eventId(),
                        notification.userId(),
                        notification.boothId(),
                        messageId);
        }
    }

    private void logError(NotificationCommand command, String errorCode, FirebaseMessagingException e) {
        switch (command) {
            case CallNotification notification ->
                log.error("FCM 푸시 알림 발송 실패 - eventId: {}, userId: {}, boothId: {}, errorCode: {}, message: {}",
                        notification.eventId(),
                        notification.userId(),
                        notification.boothId(),
                        errorCode,
                        e.getMessage(),
                        e);
        }
    }
}