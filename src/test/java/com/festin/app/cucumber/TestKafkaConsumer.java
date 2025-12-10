package com.festin.app.cucumber;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.CountDownLatch;

/**
 * 테스트용 Kafka Consumer
 * 발행된 메시지를 실제로 받을 수 있는지 확인
 */
@Component
public class TestKafkaConsumer {

    private String lastMessage;
    private CountDownLatch latch = new CountDownLatch(1);

    @KafkaListener(topics = "test-topic", groupId = "test-group")
    public void listen(ConsumerRecord<String, String> record) {
        this.lastMessage = record.value();
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