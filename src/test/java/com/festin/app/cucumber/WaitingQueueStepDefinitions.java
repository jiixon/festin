package com.festin.app.cucumber;

import com.festin.booth.adapter.out.persistence.entity.BoothEntity;
import com.festin.booth.adapter.out.persistence.repository.BoothJpaRepository;
import com.festin.booth.domain.model.BoothStatus;
import com.festin.university.adapter.out.persistence.entity.UniversityEntity;
import com.festin.university.adapter.out.persistence.repository.UniversityJpaRepository;
import com.festin.user.adapter.out.persistence.entity.UserEntity;
import com.festin.user.adapter.out.persistence.repository.UserJpaRepository;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class WaitingQueueStepDefinitions {

    @LocalServerPort
    private int port;

    @Autowired
    private UniversityJpaRepository universityRepository;

    @Autowired
    private BoothJpaRepository boothRepository;

    @Autowired
    private UserJpaRepository userRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private WebTestClient webTestClient;
    private Long testUniversityId;
    private Long testBoothId;
    private Long testUserId;
    private WebTestClient.ResponseSpec lastResponse;
    private Map<String, Object> lastResponseBody;

    @Before
    public void setup() {
        userRepository.deleteAll();
        boothRepository.deleteAll();
        universityRepository.deleteAll();

        webTestClient = WebTestClient
                .bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
        redisTemplate.getConnectionFactory()
                .getConnection()
                .flushAll();
    }

    @Given("테스트용 대학교가 존재한다")
    public void createTestUniversity() {
        String uniqueDomain = "TEST-UNIV-" + System.currentTimeMillis();
        UniversityEntity university = new UniversityEntity(
                "테스트대학교",
                uniqueDomain
        );
        testUniversityId = universityRepository.save(university).getId();
    }

    @Given("테스트용 부스가 존재한다")
    public void createTestBooth() {
        UniversityEntity university = universityRepository.findById(testUniversityId)
                .orElseThrow();

        BoothEntity booth = new BoothEntity(
                university,
                "테스트 부스",
                "부스 설명",
                10,
                BoothStatus.OPEN
        );
        testBoothId = boothRepository.save(booth).getId();
        redisTemplate.opsForValue().set("booth:" + testBoothId + ":status", "OPEN");
    }

    @Given("테스트용 사용자가 존재한다")
    public void createTestUser() {
        String uniqueEmail = "test-" + System.currentTimeMillis() + "@test.com";
        UserEntity user = new UserEntity(
                uniqueEmail,
                "테스트유저",
                "010-1234-5678"
        );
        testUserId = userRepository.save(user).getId();
    }

    @Given("사용자가 로그인되어 있다")
    public void userIsLoggedIn() {
        // X-User-Id 헤더로 인증을 대체하므로 별도 작업 불필요
    }

    @When("사용자가 부스에 대기 등록을 요청한다")
    public void userRequestsEnqueue() {
        Map<String, Object> requestBody = Map.of("boothId", testBoothId);

        lastResponse = webTestClient.post()
                .uri("/api/v1/waitings")
                .header("X-User-Id", testUserId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange();

        lastResponseBody = lastResponse
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
    }

    @Then("대기 등록이 성공한다")
    public void enqueueIsSuccessful() {
        assertThat(lastResponseBody).isNotNull();
        assertThat(lastResponseBody).containsKeys("boothId", "boothName", "position");
    }

    @Then("응답 상태 코드는 {int}이다")
    public void responseStatusCodeIs(int expectedStatusCode) {
        lastResponse.expectStatus().isEqualTo(expectedStatusCode);
    }

    @Then("응답에 부스 정보가 포함된다")
    public void responseContainsBoothInfo() {
        assertThat(lastResponseBody.get("boothId")).isEqualTo(testBoothId.intValue());
        assertThat(lastResponseBody.get("boothName")).isEqualTo("테스트 부스");
    }

    @Then("응답에 순번 정보가 포함된다")
    public void responseContainsPositionInfo() {
        assertThat(lastResponseBody).containsKeys("position", "totalWaiting", "estimatedWaitTime");
        assertThat(lastResponseBody.get("position")).isNotNull();
        assertThat(lastResponseBody.get("totalWaiting")).isNotNull();
    }

    @Given("사용자가 부스에 이미 대기 등록되어 있다")
    public void userIsAlreadyEnqueued() {
        // Redis에 직접 대기열 추가
        String queueKey = "queue:booth:" + testBoothId;
        double score = Instant.now().getEpochSecond();
        redisTemplate.opsForZSet().add(queueKey, testUserId.toString(), score);
    }

    @When("사용자가 부스의 순번을 조회한다")
    public void userRequestsPosition() {
        lastResponse = webTestClient.get()
                .uri("/api/v1/waitings/booth/" + testBoothId)
                .header("X-User-Id", testUserId.toString())
                .exchange();

        lastResponseBody = lastResponse
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
    }

    @Then("순번 조회가 성공한다")
    public void positionQueryIsSuccessful() {
        assertThat(lastResponseBody).isNotNull();
        assertThat(lastResponseBody).containsKeys("boothId", "boothName", "position");
    }

    @When("사용자가 대기를 취소한다")
    public void userCancelsWaiting() {
        lastResponse = webTestClient.delete()
                .uri("/api/v1/waitings/" + testBoothId)
                .header("X-User-Id", testUserId.toString())
                .exchange();
    }

    @Then("대기 취소가 성공한다")
    public void cancelIsSuccessful() {
        // 204 No Content이므로 body 검증 불필요
    }

    @Then("대기열에서 제거되었다")
    public void removedFromQueue() {
        String queueKey = "queue:booth:" + testBoothId;
        Long rank = redisTemplate.opsForZSet().rank(queueKey, testUserId.toString());
        assertThat(rank).isNull(); // 대기열에 없어야 함
    }
}
