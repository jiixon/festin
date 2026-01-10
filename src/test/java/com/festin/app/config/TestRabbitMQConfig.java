package com.festin.app.config;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 테스트용 RabbitMQ 큐 설정
 */
@TestConfiguration
public class TestRabbitMQConfig {

    public static final String TEST_NOTIFICATION_QUEUE = "booth-call-notifications-test";

    @Bean
    public Queue testQueue() {
        return new Queue("test-queue", false);
    }

    @Bean
    public Queue testNotificationQueue() {
        return QueueBuilder.durable(TEST_NOTIFICATION_QUEUE).build();
    }

    @Bean
    public AmqpAdmin amqpAdmin(ConnectionFactory connectionFactory) {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        admin.setAutoStartup(true);
        return admin;
    }
}