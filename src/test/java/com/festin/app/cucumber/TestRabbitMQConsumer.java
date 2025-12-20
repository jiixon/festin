package com.festin.app.cucumber;

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
    private CountDownLatch latch = new CountDownLatch(1);

    @RabbitListener(queues = "test-queue")
    public void listen(String message) {
        this.lastMessage = message;
        latch.countDown();
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public CountDownLatch getLatch() {
        return latch;
    }

    public void reset() {
        this.lastMessage = null;
        this.latch = new CountDownLatch(1);
    }
}