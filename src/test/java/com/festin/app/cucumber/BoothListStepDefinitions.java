package com.festin.app.cucumber;

import com.festin.app.booth.adapter.in.web.dto.BoothListResponse;
import com.festin.app.user.adapter.out.persistence.entity.UserEntity;
import com.festin.app.user.adapter.out.persistence.repository.UserJpaRepository;
import com.festin.app.user.domain.model.Role;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 부스 목록 조회 Step Definitions
 *
 * 도메인 특화 When/Then steps만 정의
 */
public class BoothListStepDefinitions {

    @LocalServerPort
    private int port;

    @Autowired
    private UserJpaRepository userRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private TestContext testContext;

    @And("{string}에 {int}명의 대기자가 있다")
    public void waitingUsersExistInBooth(String boothName, int waitingCount) {
        Long boothId = testContext.getBoothMap().get(boothName);
        String queueKey = "queue:booth:" + boothId;

        // Redis Sorted Set에 대기자 추가
        for (int i = 0; i < waitingCount; i++) {
            String uniqueEmail = "user" + i + "-" + System.currentTimeMillis() + "@test.com";
            UserEntity user = new UserEntity(uniqueEmail, "테스트유저" + i, Role.VISITOR);
            Long userId = userRepository.save(user).getId();

            // Redis에 대기 추가
            double score = LocalDateTime.now().plusSeconds(i).atZone(java.time.ZoneId.systemDefault()).toEpochSecond();
            redisTemplate.opsForZSet().add(queueKey, String.valueOf(userId), score);
        }
    }

    @When("전체 부스 목록을 조회한다")
    public void getBoothList() {
        WebTestClient webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();

        BoothListResponse response = webTestClient.get()
                .uri("/api/v1/booths")
                .exchange()
                .expectStatus().isOk()
                .expectBody(BoothListResponse.class)
                .returnResult()
                .getResponseBody();

        testContext.setBoothListResponse(response);
    }

    @When("{string}의 부스 목록을 조회한다")
    public void getBoothListByUniversity(String universityName) {
        WebTestClient webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();

        Long universityId = testContext.getUniversityMap().get(universityName);

        BoothListResponse response = webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/booths")
                        .queryParam("universityId", universityId)
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(BoothListResponse.class)
                .returnResult()
                .getResponseBody();

        testContext.setBoothListResponse(response);
    }

    @Then("응답에 {int}개의 부스가 포함된다")
    public void responseContainsBooths(int expectedCount) {
        assertThat(testContext.getBoothListResponse().booths()).hasSize(expectedCount);
    }

    @And("부스 목록에 {string}가 포함된다")
    public void boothListContainsBooth(String boothName) {
        boolean exists = testContext.getBoothListResponse().booths().stream()
                .anyMatch(booth -> booth.boothName().equals(boothName));
        assertThat(exists).isTrue();
    }

    @Then("{string}의 대기 인원은 {int}명이다")
    public void boothHasWaitingCount(String boothName, int expectedWaiting) {
        BoothListResponse.BoothItem booth = testContext.getBoothListResponse().booths().stream()
                .filter(b -> b.boothName().equals(boothName))
                .findFirst()
                .orElseThrow();

        assertThat(booth.currentWaiting()).isEqualTo(expectedWaiting);
    }

    @And("{string}의 예상 대기 시간은 {int}분이다")
    public void boothHasEstimatedWaitTime(String boothName, int expectedTime) {
        BoothListResponse.BoothItem booth = testContext.getBoothListResponse().booths().stream()
                .filter(b -> b.boothName().equals(boothName))
                .findFirst()
                .orElseThrow();

        assertThat(booth.estimatedWaitTime()).isEqualTo(expectedTime);
    }
}