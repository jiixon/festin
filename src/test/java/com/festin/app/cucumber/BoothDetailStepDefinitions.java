package com.festin.app.cucumber;

import com.festin.app.booth.adapter.in.web.dto.BoothDetailResponse;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 부스 상세 조회 Step Definitions
 *
 * 도메인 특화 When/Then steps만 정의
 */
public class BoothDetailStepDefinitions {

    @LocalServerPort
    private int port;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private TestContext testContext;

    @And("{string}에 {int}명이 입장했다")
    public void peopleEnteredBooth(String boothName, int enteredCount) {
        Long boothId = testContext.getBoothMap().get(boothName);
        String currentKey = "booth:" + boothId + ":current";

        // Redis에 현재 입장 인원 저장
        redisTemplate.opsForValue().set(currentKey, String.valueOf(enteredCount));
    }

    @When("{string}의 상세 정보를 조회한다")
    public void getBoothDetail(String boothName) {
        WebTestClient webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();

        Long boothId = testContext.getBoothMap().get(boothName);

        BoothDetailResponse response = webTestClient.get()
                .uri("/api/v1/booths/" + boothId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(BoothDetailResponse.class)
                .returnResult()
                .getResponseBody();

        testContext.setBoothDetailResponse(response);
    }

    @Then("부스 이름은 {string}이다")
    public void boothNameIs(String expectedName) {
        assertThat(testContext.getBoothDetailResponse().boothName()).isEqualTo(expectedName);
    }

    @And("부스 대학은 {string}이다")
    public void boothUniversityIs(String expectedUniversityName) {
        assertThat(testContext.getBoothDetailResponse().universityName()).isEqualTo(expectedUniversityName);
    }

    @And("부스 정원은 {int}명이다")
    public void boothCapacityIs(int expectedCapacity) {
        assertThat(testContext.getBoothDetailResponse().capacity()).isEqualTo(expectedCapacity);
    }

    @And("현재 입장 인원은 {int}명이다")
    public void currentPeopleIs(int expectedCurrent) {
        assertThat(testContext.getBoothDetailResponse().currentPeople()).isEqualTo(expectedCurrent);
    }

    @And("대기 인원은 {int}명이다")
    public void totalWaitingIs(int expectedWaiting) {
        assertThat(testContext.getBoothDetailResponse().totalWaiting()).isEqualTo(expectedWaiting);
    }

    @And("예상 대기 시간은 {int}분이다")
    public void estimatedWaitTimeIs(int expectedTime) {
        assertThat(testContext.getBoothDetailResponse().estimatedWaitTime()).isEqualTo(expectedTime);
    }
}