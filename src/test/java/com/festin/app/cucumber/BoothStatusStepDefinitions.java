package com.festin.app.cucumber;

import com.festin.app.booth.adapter.in.web.dto.BoothStatusResponse;
import com.festin.app.fixture.UserFixture;
import com.festin.app.fixture.WaitingFixtureBuilder;
import com.festin.app.user.adapter.out.persistence.entity.UserEntity;
import com.festin.app.user.adapter.out.persistence.repository.UserJpaRepository;
import com.festin.app.user.domain.model.Role;
import com.festin.app.waiting.domain.model.CompletionType;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 부스 현황 조회 Step Definitions
 *
 * Fixture Builder 패턴 적용:
 * - Fixture로 복잡한 상태 생성
 * - DB/Redis 동기화 자동화
 */
public class BoothStatusStepDefinitions {

    @LocalServerPort
    private int port;

    @Autowired
    private WaitingFixtureBuilder waitingFixtureBuilder;

    @Autowired
    private UserFixture userFixture;

    @Autowired
    private UserJpaRepository userRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private TestContext testContext;

    @And("{string}에 CALLED 상태 {int}개가 존재한다 \\(오늘)")
    public void boothHasCalledRecords(String boothName, int count) {
        Long boothId = testContext.getBoothMap().get(boothName);
        LocalDateTime baseTime = LocalDateTime.now();

        for (int i = 1; i <= count; i++) {
            Long userId = userFixture.createVisitor("called-" + i);

            waitingFixtureBuilder
                    .forUser(userId)
                    .forBooth(boothId)
                    .position(i)
                    .statusCalled(baseTime.minusMinutes(count - i))
                    .build();
        }
    }

    @And("{string}에 ENTERED 상태 {int}개가 존재한다 \\(오늘)")
    public void boothHasEnteredRecords(String boothName, int count) {
        Long boothId = testContext.getBoothMap().get(boothName);
        LocalDateTime baseTime = LocalDateTime.now();

        for (int i = 1; i <= count; i++) {
            Long userId = userFixture.createVisitor("entered-" + i);

            waitingFixtureBuilder
                    .forUser(userId)
                    .forBooth(boothId)
                    .position(i)
                    .statusEntered(
                            baseTime.minusMinutes(count - i + 20),  // calledAt
                            baseTime.minusMinutes(count - i + 10)   // enteredAt
                    )
                    .build();
        }
    }

    @And("{string}에 정상 완료 건 {int}개가 존재한다 \\(오늘)")
    public void boothHasCompletedRecords(String boothName, int count) {
        Long boothId = testContext.getBoothMap().get(boothName);
        LocalDateTime baseTime = LocalDateTime.now();

        for (int i = 1; i <= count; i++) {
            Long userId = userFixture.createVisitor("completed-" + i);

            waitingFixtureBuilder
                    .forUser(userId)
                    .forBooth(boothId)
                    .position(i)
                    .statusCompleted(
                            CompletionType.ENTERED,
                            baseTime.minusMinutes(count - i + 20),  // calledAt (max 21분 전)
                            baseTime.minusMinutes(count - i + 15),  // enteredAt
                            baseTime.minusMinutes(count - i + 10)   // completedAt
                    )
                    .build();
        }
    }

    @And("{string}에 노쇼 건 {int}개가 존재한다 \\(오늘)")
    public void boothHasNoShowRecords(String boothName, int count) {
        Long boothId = testContext.getBoothMap().get(boothName);
        LocalDateTime baseTime = LocalDateTime.now();

        for (int i = 1; i <= count; i++) {
            Long userId = userFixture.createVisitor("noshow-" + i);

            waitingFixtureBuilder
                    .forUser(userId)
                    .forBooth(boothId)
                    .position(i)
                    .statusNoShow(
                            baseTime.minusMinutes(count - i + 10),  // calledAt
                            baseTime.minusMinutes(count - i + 5)    // completedAt (타임아웃)
                    )
                    .build();
        }
    }

    @And("{string}의 대기열에 {int}명이 대기 중이다")
    public void peopleAreWaiting(String boothName, int waitingCount) {
        Long boothId = testContext.getBoothMap().get(boothName);

        // Redis 대기열에 추가
        String queueKey = "queue:booth:" + boothId;
        long baseScore = Instant.now().getEpochSecond();

        for (int i = 1; i <= waitingCount; i++) {
            String uniqueEmail = "waiting-user-" + boothId + "-" + i + "-" + System.currentTimeMillis() + "@test.com";
            UserEntity user = new UserEntity(uniqueEmail, "대기자" + i, Role.VISITOR);
            Long userId = userRepository.save(user).getId();

            double score = baseScore + i;
            redisTemplate.opsForZSet().add(queueKey, userId.toString(), score);
        }
    }

    @When("스태프가 {string}의 현황을 조회한다")
    public void staffGetsBoothStatus(String boothName) {
        WebTestClient webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();

        Long boothId = testContext.getBoothMap().get(boothName);

        BoothStatusResponse response = webTestClient.get()
                .uri("/api/v1/booths/" + boothId + "/status")
                .exchange()
                .expectStatus().isOk()
                .expectBody(BoothStatusResponse.class)
                .returnResult()
                .getResponseBody();

        testContext.setBoothStatusResponse(response);
    }

    @Then("부스 현황에 부스 이름 {string}가 포함된다")
    public void boothStatusContainsName(String expectedName) {
        assertThat(testContext.getBoothStatusResponse().boothName()).isEqualTo(expectedName);
    }

    @And("부스 현황의 정원은 {int}명이다")
    public void boothStatusCapacityIs(int expectedCapacity) {
        assertThat(testContext.getBoothStatusResponse().capacity()).isEqualTo(expectedCapacity);
    }

    @And("부스 현황의 현재 인원은 {int}명이다")
    public void boothStatusCurrentPeopleIs(int expectedCurrent) {
        assertThat(testContext.getBoothStatusResponse().currentPeople()).isEqualTo(expectedCurrent);
    }

    @And("부스 현황의 대기 인원은 {int}명이다")
    public void boothStatusTotalWaitingIs(int expectedWaiting) {
        assertThat(testContext.getBoothStatusResponse().totalWaiting()).isEqualTo(expectedWaiting);
    }

    @And("오늘 호출된 인원은 {int}명이다")
    public void todayTotalCalledIs(int expectedCalled) {
        assertThat(testContext.getBoothStatusResponse().todayStats().totalCalled()).isEqualTo(expectedCalled);
    }

    @And("오늘 입장한 인원은 {int}명이다")
    public void todayTotalEnteredIs(int expectedEntered) {
        assertThat(testContext.getBoothStatusResponse().todayStats().totalEntered()).isEqualTo(expectedEntered);
    }

    @And("오늘 노쇼 인원은 {int}명이다")
    public void todayTotalNoShowIs(int expectedNoShow) {
        assertThat(testContext.getBoothStatusResponse().todayStats().totalNoShow()).isEqualTo(expectedNoShow);
    }

    @And("오늘 정상 완료 인원은 {int}명이다")
    public void todayTotalCompletedIs(int expectedCompleted) {
        assertThat(testContext.getBoothStatusResponse().todayStats().totalCompleted()).isEqualTo(expectedCompleted);
    }
}
