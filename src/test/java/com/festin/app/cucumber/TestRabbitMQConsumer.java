package com.festin.app.cucumber;

import com.festin.app.waiting.application.port.out.NotificationPort.NotificationCommand;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.CountDownLatch;

/**
 * 테스트용 RabbitMQ Consumer
 * 발행된 메시지를 실제로 받을 수 있는지 확인
 */
@Component
public class TestRabbitMQConsumer {

    private String lastMessage;
    private NotificationCommand lastNotification;
    private CountDownLatch latch = new CountDownLatch(1);
    private CountDownLatch notificationLatch = new CountDownLatch(1);

    @RabbitListener(queues = "test-queue")
    public void listen(String message) {
        this.lastMessage = message;
        latch.countDown();
    }

    @RabbitListener(queues = "booth-call-notifications")
    public void listenNotification(NotificationCommand notification) {
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