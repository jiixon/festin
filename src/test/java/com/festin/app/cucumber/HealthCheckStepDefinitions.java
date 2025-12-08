package com.festin.app.cucumber;

import com.festin.app.adapter.ApiTestAdapter;
import com.festin.app.adapter.CacheTestAdapter;
import com.festin.app.adapter.DatabaseTestAdapter;
import com.festin.app.adapter.MessageQueueTestAdapter;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

public class HealthCheckStepDefinitions {

    @Autowired
    private ApiTestAdapter apiTestAdapter;

    @Autowired
    private DatabaseTestAdapter databaseTestAdapter;

    @Autowired
    private CacheTestAdapter cacheTestAdapter;

    @Autowired
    private MessageQueueTestAdapter messageQueueTestAdapter;

    private String healthCheckStatus;
    private boolean databaseConnectionSuccess;
    private boolean cacheConnectionSuccess;
    private boolean kafkaConnectionSuccess;

    @Given("애플리케이션이 실행중이다")
    public void theApplicationIsRunning() {
        // Spring Boot 테스트가 이미 실행중이므로 별도 작업 불필요
    }

    @When("\\/api\\/health 엔드포인트를 호출한다")
    public void iCallTheHealthEndpoint() {
        healthCheckStatus = apiTestAdapter.getStatusFromHealthCheck();
    }

    @Then("응답 status는 {string}이다")
    public void theResponseStatusShouldBe(String expectedStatus) {
        assertThat(healthCheckStatus).isEqualTo(expectedStatus);
    }

    @When("MySQL 데이터베이스에 연결을 시도한다")
    public void iTryToConnectToMySQLDatabase() {
        databaseConnectionSuccess = databaseTestAdapter.canConnect();
    }

    @Then("MySQL 연결이 성공한다")
    public void mysqlConnectionShouldSucceed() {
        assertThat(databaseConnectionSuccess).isTrue();
    }

    @When("Redis에 데이터를 저장하고 조회한다")
    public void iSaveAndRetrieveDataFromRedis() {
        cacheConnectionSuccess = cacheTestAdapter.canConnect();
    }

    @Then("Redis 연결이 성공한다")
    public void redisConnectionShouldSucceed() {
        assertThat(cacheConnectionSuccess).isTrue();
    }

    @When("Kafka 브로커에 연결을 시도한다")
    public void iTryToConnectToKafkaBroker() {
        kafkaConnectionSuccess = messageQueueTestAdapter.canConnect();
    }

    @Then("Kafka 연결이 성공한다")
    public void kafkaConnectionShouldSucceed() {
        assertThat(kafkaConnectionSuccess).isTrue();
    }
}