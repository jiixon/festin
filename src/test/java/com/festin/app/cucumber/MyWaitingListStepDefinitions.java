package com.festin.app.cucumber;

import com.festin.app.waiting.adapter.in.web.dto.MyWaitingListResponse;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 내 대기 목록 조회 Step Definitions
 *
 * 도메인 특화 When/Then steps만 정의
 */
public class MyWaitingListStepDefinitions {

    @LocalServerPort
    private int port;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private TestContext testContext;

    @And("{string}이 {string}에 대기 등록했다")
    public void userEnqueuedToBooth(String username, String boothName) {
        Long userId = testContext.getUserMap().get(username);
        Long boothId = testContext.getBoothMap().get(boothName);

        // Redis Sorted Set에 대기 추가
        String queueKey = "queue:booth:" + boothId;
        double score = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        redisTemplate.opsForZSet().add(queueKey, String.valueOf(userId), score);

        // 사용자 활성 부스 목록에 추가
        String activeBoothsKey = "user:" + userId + ":active_booths";
        redisTemplate.opsForSet().add(activeBoothsKey, String.valueOf(boothId));
    }

    @And("{string}에서 다음 사람을 호출했다")
    public void callNextPersonFromBooth(String boothName) {
        WebTestClient webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();

        Long boothId = testContext.getBoothMap().get(boothName);

        // API 호출
        webTestClient.post()
                .uri("/api/v1/waitings/call")
                .bodyValue(Map.of("boothId", boothId))
                .exchange()
                .expectStatus().isOk();
    }

    @When("{string}이 내 대기 목록을 조회한다")
    public void getMyWaitingList(String username) {
        WebTestClient webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();

        Long userId = testContext.getUserMap().get(username);

        MyWaitingListResponse response = webTestClient.get()
                .uri("/api/v1/waitings/my")
                .header("X-User-Id", String.valueOf(userId))
                .exchange()
                .expectStatus().isOk()
                .expectBody(MyWaitingListResponse.class)
                .returnResult()
                .getResponseBody();

        testContext.setMyWaitingListResponse(response);
    }

    @Then("대기 목록은 비어있다")
    public void waitingListIsEmpty() {
        assertThat(testContext.getMyWaitingListResponse().waitings()).isEmpty();
    }

    @Then("대기 목록 크기는 {int}이다")
    public void waitingListSizeIs(int expectedSize) {
        assertThat(testContext.getMyWaitingListResponse().waitings()).hasSize(expectedSize);
    }

    @And("첫 번째 대기 항목의 부스 이름은 {string}이다")
    public void firstWaitingItemBoothNameIs(String expectedBoothName) {
        assertThat(testContext.getMyWaitingListResponse().waitings().get(0).boothName()).isEqualTo(expectedBoothName);
    }

    @And("첫 번째 대기 항목의 상태는 {string}이다")
    public void firstWaitingItemStatusIs(String expectedStatus) {
        assertThat(testContext.getMyWaitingListResponse().waitings().get(0).status()).isEqualTo(expectedStatus);
    }

    @And("첫 번째 대기 항목의 순번은 {int}이다")
    public void firstWaitingItemPositionIs(int expectedPosition) {
        assertThat(testContext.getMyWaitingListResponse().waitings().get(0).position()).isEqualTo(expectedPosition);
    }

    @And("첫 번째 대기 항목의 총 대기 인원은 {int}명이다")
    public void firstWaitingItemTotalWaitingIs(int expectedTotal) {
        assertThat(testContext.getMyWaitingListResponse().waitings().get(0).totalWaiting()).isEqualTo(expectedTotal);
    }

    @And("첫 번째 대기 항목의 예상 대기 시간은 {int}분이다")
    public void firstWaitingItemEstimatedWaitTimeIs(int expectedTime) {
        assertThat(testContext.getMyWaitingListResponse().waitings().get(0).estimatedWaitTime()).isEqualTo(expectedTime);
    }
}