package com.festin.app.common.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 알림 큐 설정
 *
 * RabbitMQ 큐 및 메시지 컨버터 설정
 * @JsonTypeInfo와 @JsonSubTypes를 통한 다형성 직렬화 지원
 */
@Configuration
public class NotificationQueueConfig {

    public static final String NOTIFICATION_QUEUE = "booth-call-notifications";

    /**
     * 호출 알림 큐
     *
     * - durable: true (재시작 시에도 큐 유지)
     * - exclusive: false (다른 연결에서도 접근 가능)
     * - autoDelete: false (컨슈머가 없어도 큐 유지)
     */
    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE)
                .build();
    }

    /**
     * JSON 메시지 컨버터
     *
     * Jackson2JsonMessageConverter가 NotificationCommand의
     * sealed interface 다형성을 처리합니다.
     * @JsonTypeInfo와 @JsonSubTypes 어노테이션이 자동으로 적용됩니다.
     */
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}