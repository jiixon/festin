package com.festin.cucumber;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
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
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private TestKafkaConsumer testKafkaConsumer;

    private Map<String, Object> healthCheckResponse;
    private boolean databaseConnectionSuccess;
    private boolean cacheConnectionSuccess;

    @Given("애플리케이션이 실행중이다")
    public void theApplicationIsRunning() {
        // Spring Boot 테스트가 이미 실행중이므로 별도 작업 불필요
    }

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

    @When("Kafka 연결을 테스트한다")
    public void iTestKafkaConnection() {
        try {
            testKafkaConsumer.reset();
            String testMsg = "test-message-" + UUID.randomUUID();
            kafkaTemplate.send("test-topic", testMsg).get(5, TimeUnit.SECONDS);
            testKafkaConsumer.getLatch().await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
        }
    }

    @Then("Kafka가 정상 동작한다")
    public void kafkaShouldWork() {
        String consumedMessage = testKafkaConsumer.getLastMessage();
        assertThat(consumedMessage)
                .as("Kafka에서 발행한 메시지를 Consumer가 정상적으로 받아야 합니다")
                .isNotNull()
                .startsWith("test-message-");
    }
}