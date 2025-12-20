package com.festin.app.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    MySQLContainer<?> mysqlContainer() {
        return new MySQLContainer<>(DockerImageName.parse("mysql:8.0"));
    }

    @Bean
    @ServiceConnection(name = "redis")
    org.testcontainers.containers.GenericContainer<?> redisContainer() {
        return new org.testcontainers.containers.GenericContainer<>(DockerImageName.parse("redis:7.0-alpine"))
                .withExposedPorts(6379);
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbitMQContainer() {
        return new RabbitMQContainer(DockerImageName.parse("rabbitmq:3-management-alpine"))
                .waitingFor(Wait.forListeningPort())  // 포트 열릴 때까지 대기
                .withStartupTimeout(Duration.ofMinutes(2));  // 타임아웃 여유있게
    }
}
