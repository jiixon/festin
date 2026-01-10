package com.festin.app.cucumber;

import com.festin.app.config.TestRabbitMQConfig;
import com.festin.app.waiting.application.port.out.NotificationPort.NotificationCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.CountDownLatch;

/**
 * 테스트용 RabbitMQ Consumer
 *
 * 테스트 전용 큐를 사용하여 festin-app과의 메시지 충돌 방지
 */
@Component
public class TestRabbitMQConsumer {

    private static final Logger log = LoggerFactory.getLogger(TestRabbitMQConsumer.class);

    private String lastMessage;
    private NotificationCommand lastNotification;
    private CountDownLatch latch = new CountDownLatch(1);
    private CountDownLatch notificationLatch = new CountDownLatch(1);

    @RabbitListener(queues = "test-queue")
    public void listen(String message) {
        log.info("[TestRabbitMQConsumer] 메시지 수신: {}", message);
        this.lastMessage = message;
        latch.countDown();
    }

    @RabbitListener(queues = TestRabbitMQConfig.TEST_NOTIFICATION_QUEUE)
    public void listenNotification(NotificationCommand notification) {
        log.info("[TestRabbitMQConsumer] 알림 메시지 수신: {}", notification);
        this.lastNotification = notification;
        notificationLatch.countDown();
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public NotificationCommand getLastNotification() {
        return lastNotification;
    }

    public CountDownLatch getLatch() {
        return latch;
    }

    public CountDownLatch getNotificationLatch() {
        return notificationLatch;
    }

    public void reset() {
        this.lastMessage = null;
        this.lastNotification = null;
        this.latch = new CountDownLatch(1);
        this.notificationLatch = new CountDownLatch(1);
    }
}