package com.festin.app.cucumber;

import com.festin.app.booth.adapter.out.persistence.entity.BoothEntity;
import com.festin.app.booth.adapter.out.persistence.repository.BoothJpaRepository;
import com.festin.app.booth.domain.model.BoothStatus;
import com.festin.app.university.adapter.out.persistence.entity.UniversityEntity;
import com.festin.app.university.adapter.out.persistence.repository.UniversityJpaRepository;
import com.festin.app.user.adapter.out.persistence.entity.UserEntity;
import com.festin.app.user.adapter.out.persistence.repository.UserJpaRepository;
import com.festin.app.user.domain.model.Role;
import com.festin.app.waiting.adapter.out.persistence.repository.WaitingJpaRepository;
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
    private WaitingJpaRepository waitingJpaRepository;

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
    private Long lastWaitingId;

    @Before
    public void setup() {
        waitingJpaRepository.deleteAll();
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

        // Redis에 부스 메타 정보 저장
        String metaKey = "booth:" + testBoothId + ":meta";
        redisTemplate.opsForHash().put(metaKey, "status", "OPEN");
        redisTemplate.opsForHash().put(metaKey, "name", "테스트 부스");
        redisTemplate.opsForHash().put(metaKey, "capacity", "10");


        String currentKey = "booth:" + testBoothId + ":current";
        redisTemplate.opsForValue().set(currentKey, "0");
    }

    @Given("테스트용 사용자가 존재한다")
    public void createTestUser() {
        String uniqueEmail = "test-" + System.currentTimeMillis() + "@test.com";
        UserEntity user = new UserEntity(
                uniqueEmail,
                "테스트유저",
                Role.VISITOR
        );
        user.updateFcmToken("fD7sXkPqR8u9ZcE4YVw2K3M0B1A6JHnO_LmTQp5iUeRZxC");
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
        String queueKey = "queue:booth:" + testBoothId;
        double score = Instant.now().getEpochSecond();
        redisTemplate.opsForZSet().add(queueKey, testUserId.toString(), score);

        // 사용자 활성 부스 목록에도 추가
        String activeBoothsKey = "user:" + testUserId + ":active_booths";
        redisTemplate.opsForSet().add(activeBoothsKey, testBoothId.toString());
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
        assertThat(rank).isNull();
    }

    @Given("부스에 대기 중인 사용자가 존재한다")
    public void boothHasWaitingUser() {
        // 대기열에 추가 (score에 등록 시간 포함)
        String queueKey = "queue:booth:" + testBoothId;
        double score = Instant.now().getEpochSecond();
        redisTemplate.opsForZSet().add(queueKey, testUserId.toString(), score);

        // 사용자 활성 부스 목록에도 추가
        String activeBoothsKey = "user:" + testUserId + ":active_booths";
        redisTemplate.opsForSet().add(activeBoothsKey, testBoothId.toString());
    }

    @When("스태프가 다음 사람을 호출한다")
    @Given("스태프가 사용자를 호출했다")
    public void staffCallsNextUser() {
        Map<String, Object> requestBody = Map.of(
                "boothId", testBoothId
        );

        lastResponse = webTestClient.post()
                .uri("/api/v1/waitings/call")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange();

        lastResponseBody = lastResponse
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

        // waitingId 저장 (입장 확인 시 사용)
        if (lastResponseBody != null && lastResponseBody.containsKey("waitingId")) {
            lastWaitingId = ((Number) lastResponseBody.get("waitingId")).longValue();
        }
    }

    @Then("호출이 성공한다")
    public void callIsSuccessful() {
        assertThat(lastResponseBody).isNotNull();
    }

    @Then("호출 결과에 사용자 정보가 포함된다")
    public void responseContainsCallResult() {
        assertThat(lastResponseBody).containsKeys(
                "waitingId",
                "userId",
                "position",
                "calledAt"
        );

        assertThat(lastResponseBody.get("userId"))
                .isEqualTo(testUserId.intValue());
    }

    @Then("대기열에서 호출된 사용자는 제거되었다")
    public void calledUserIsRemovedFromQueue() {
        String queueKey = "queue:booth:" + testBoothId;
        Long rank = redisTemplate.opsForZSet()
                .rank(queueKey, testUserId.toString());

        assertThat(rank).isNull();
    }

    @Then("호출된 사용자는 대기 기록으로 저장된다")
    public void waitingIsPersisted() {
        assertThat(waitingJpaRepository.count()).isEqualTo(1);
    }

    @Then("사용자 활성 부스 목록에서 제거되었다")
    public void removedFromActiveBooths() {
        String activeBoothsKey = "user:" + testUserId + ":active_booths";
        Boolean isMember = redisTemplate.opsForSet()
                .isMember(activeBoothsKey, testBoothId.toString());
        assertThat(isMember).isFalse();
    }

    @When("스태프가 입장을 확인한다")
    @Given("스태프가 입장을 확인했다")
    public void staffConfirmsEntrance() {
        lastResponse = webTestClient.post()
                .uri("/api/v1/booths/" + testBoothId + "/entrance/" + lastWaitingId)
                .exchange();

        lastResponseBody = lastResponse
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
    }

    @Then("입장 확인이 성공한다")
    public void entranceConfirmationIsSuccessful() {
        assertThat(lastResponseBody).isNotNull();
    }

    @Then("응답에 입장 확인 정보가 포함된다")
    public void responseContainsEntranceInfo() {
        assertThat(lastResponseBody).containsKeys("waitingId", "status", "enteredAt");
        assertThat(lastResponseBody.get("waitingId")).isEqualTo(lastWaitingId.intValue());
        assertThat(lastResponseBody.get("status")).isEqualTo("ENTERED");
        assertThat(lastResponseBody.get("enteredAt")).isNotNull();
    }

    @Then("부스 현재 인원이 증가했다")
    public void boothCurrentCountIncreased() {
        String currentKey = "booth:" + testBoothId + ":current";
        String currentCount = redisTemplate.opsForValue().get(currentKey);
        assertThat(currentCount).isNotNull();
        assertThat(Integer.parseInt(currentCount)).isEqualTo(1);
    }

    @When("스태프가 체험 완료 처리한다")
    public void staffCompletesExperience() {
        lastResponse = webTestClient.post()
                .uri("/api/v1/booths/" + testBoothId + "/complete/" + lastWaitingId)
                .exchange();

        lastResponseBody = lastResponse
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
    }

    @Then("체험 완료가 성공한다")
    public void experienceCompletionIsSuccessful() {
        assertThat(lastResponseBody).isNotNull();
    }

    @Then("응답에 체험 완료 정보가 포함된다")
    public void responseContainsCompletionInfo() {
        assertThat(lastResponseBody).containsKeys("waitingId", "status", "completionType", "completedAt");
        assertThat(lastResponseBody.get("waitingId")).isEqualTo(lastWaitingId.intValue());
        assertThat(lastResponseBody.get("status")).isEqualTo("COMPLETED");
        assertThat(lastResponseBody.get("completionType")).isEqualTo("ENTERED");
        assertThat(lastResponseBody.get("completedAt")).isNotNull();
    }

    @Then("부스 현재 인원이 감소했다")
    public void boothCurrentCountDecreased() {
        String currentKey = "booth:" + testBoothId + ":current";
        String currentCount = redisTemplate.opsForValue().get(currentKey);
        assertThat(currentCount).isNotNull();
        assertThat(Integer.parseInt(currentCount)).isEqualTo(0);
    }
}
