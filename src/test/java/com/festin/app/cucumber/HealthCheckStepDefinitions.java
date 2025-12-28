package com.festin.app.cucumber;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class HealthCheckStepDefinitions {

    @LocalServerPort
    private int port;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private TestRabbitMQConsumer testRabbitMQConsumer;

    private Map<String, Object> healthCheckResponse;
    private boolean databaseConnectionSuccess;
    private boolean cacheConnectionSuccess;

    @When("{string} 엔드포인트를 호출한다")
    public void iCallTheHealthEndpoint(String endpoint) {
        WebTestClient client = WebTestClient
                .bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();

        healthCheckResponse = client.get()
                .uri(endpoint)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
    }

    @Then("응답 status는 {string}이다")
    public void theResponseStatusShouldBe(String expectedStatus) {
        String actualStatus = (String) healthCheckResponse.get("status");
        assertThat(actualStatus).isEqualTo(expectedStatus);
    }

    @When("MySQL 데이터베이스에 연결을 시도한다")
    public void iTryToConnectToMySQLDatabase() {
        try (Connection connection = dataSource.getConnection()) {
            databaseConnectionSuccess = connection.isValid(5);
        } catch (Exception e) {
            databaseConnectionSuccess = false;
        }
    }

    @Then("MySQL 연결이 성공한다")
    public void mysqlConnectionShouldSucceed() {
        assertThat(databaseConnectionSuccess).isTrue();
    }

    @When("Redis에 데이터를 저장하고 조회한다")
    public void iSaveAndRetrieveDataFromRedis() {
        try {
            String key = "test:connection:" + UUID.randomUUID();
            String value = "test-value";
            redisTemplate.opsForValue().set(key, value);
            String retrieved = redisTemplate.opsForValue().get(key);
            redisTemplate.delete(key);
            cacheConnectionSuccess = value.equals(retrieved);
        } catch (Exception e) {
            cacheConnectionSuccess = false;
        }
    }

    @Then("Redis 연결이 성공한다")
    public void redisConnectionShouldSucceed() {
        assertThat(cacheConnectionSuccess).isTrue();
    }

    @When("RabbitMQ 연결을 테스트한다")
    public void iTestRabbitMQConnection() {
        try {
            testRabbitMQConsumer.reset();
            String testMsg = "test-message-" + UUID.randomUUID();

            // 큐가 존재하는지 확인하고 없으면 생성
            rabbitTemplate.execute(channel -> {
                channel.queueDeclare("test-queue", false, false, false, null);
                return null;
            });

            rabbitTemplate.convertAndSend("test-queue", testMsg);
            testRabbitMQConsumer.getLatch().await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
        }
    }

    @Then("RabbitMQ가 정상 동작한다")
    public void rabbitMQShouldWork() {
        String consumedMessage = testRabbitMQConsumer.getLastMessage();
        assertThat(consumedMessage)
                .as("RabbitMQ에서 발행한 메시지를 Consumer가 정상적으로 받아야 합니다")
                .isNotNull()
                .startsWith("test-message-");
    }
}