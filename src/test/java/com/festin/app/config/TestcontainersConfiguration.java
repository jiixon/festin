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
        System.out.println("=== MySQL Container Configuration Started ===");
        MySQLContainer<?> container = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                .withReuse(false)
                .waitingFor(Wait.forLogMessage(".*ready for connections.*", 2))
                .withStartupTimeout(Duration.ofMinutes(3))
                .withCommand("--default-authentication-plugin=mysql_native_password");
        System.out.println("=== MySQL Container Configuration Completed ===");
        return container;
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        System.out.println("=== Redis Container Configuration Started ===");
        GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse("redis:7.0-alpine"))
                .withExposedPorts(6379)
                .withReuse(false)
                .waitingFor(Wait.forListeningPort())
                .withStartupTimeout(Duration.ofMinutes(2));
        System.out.println("=== Redis Container Configuration Completed ===");
        return container;
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbitMQContainer() {
        System.out.println("=== RabbitMQ Container Configuration Started ===");
        RabbitMQContainer container = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3-management-alpine"))
                .withReuse(false)
                .waitingFor(Wait.forListeningPort())
                .withStartupTimeout(Duration.ofMinutes(2));
        System.out.println("=== RabbitMQ Container Configuration Completed ===");
        return container;
    }
}
