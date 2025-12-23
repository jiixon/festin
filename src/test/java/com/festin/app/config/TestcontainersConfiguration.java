package com.festin.app.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

@TestConfiguration(proxyBeanMethods = false)
@ConditionalOnProperty(
    name = "spring.testcontainers.enabled",
    havingValue = "true",
    matchIfMissing = false
)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    MySQLContainer<?> mysqlContainer() {
        System.out.println("=== MySQL Container Starting ===");
        return new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                .withReuse(false)
                .waitingFor(Wait.forLogMessage(".*ready for connections.*", 2))
                .withStartupTimeout(Duration.ofMinutes(3));
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        System.out.println("=== Redis Container Starting ===");
        return new GenericContainer<>(DockerImageName.parse("redis:7.0-alpine"))
                .withExposedPorts(6379)
                .withReuse(false)
                .waitingFor(Wait.forListeningPort())
                .withStartupTimeout(Duration.ofMinutes(2));
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbitMQContainer() {
        RabbitMQContainer container = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3-management-alpine"))
                .withReuse(false)
                .waitingFor(Wait.forListeningPort())
                .withStartupTimeout(Duration.ofMinutes(2));
        System.out.println("=== RabbitMQ Container Starting ===");
        return container;
    }
}
