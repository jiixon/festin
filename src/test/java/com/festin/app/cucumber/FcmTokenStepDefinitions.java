package com.festin.app.cucumber;

import com.festin.app.fixture.UserFixture;
import com.festin.app.user.adapter.in.web.dto.UpdateFcmTokenRequest;
import com.festin.app.user.adapter.in.web.dto.UpdateFcmTokenResponse;
import com.festin.app.user.adapter.out.persistence.entity.UserEntity;
import com.festin.app.user.adapter.out.persistence.repository.UserJpaRepository;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FCM 토큰 저장 Step Definitions
 *
 * Feature: 푸시 알림 설정
 */
public class FcmTokenStepDefinitions {

    @LocalServerPort
    private int port;

    @Autowired
    private UserFixture userFixture;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private TestContext testContext;

    @Given("사용자 {string}이 로그인되어 있다")
    public void userIsLoggedIn(String username) {
        Long userId = userFixture.createVisitor(username);
        testContext.getUserMap().put(username, userId);
    }

    @When("{string}이 기기 토큰을 등록한다")
    public void userRegistersDeviceToken(String username) {
        Long userId = testContext.getUserMap().get(username);
        String fcmToken = "fcm_token_" + System.currentTimeMillis();

        WebTestClient client = WebTestClient
                .bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();

        UpdateFcmTokenRequest request = new UpdateFcmTokenRequest(fcmToken);

        UpdateFcmTokenResponse response = client.post()
                .uri("/api/v1/users/fcm-token")
                .header("X-User-Id", userId.toString())
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(UpdateFcmTokenResponse.class)
                .returnResult()
                .getResponseBody();

        testContext.setUpdateFcmTokenResponse(response);
        testContext.getUserMap().put(username + "_fcmToken", Long.parseLong(fcmToken.substring(10)));
    }

    @Then("푸시 알림 활성화에 성공한다")
    public void pushNotificationActivationSucceeds() {
        assertThat(testContext.getUpdateFcmTokenResponse()).isNotNull();
        assertThat(testContext.getUpdateFcmTokenResponse().success()).isTrue();
    }

    @And("{string}은 이제 푸시 알림을 받을 수 있다")
    public void userCanNowReceivePushNotifications(String username) {
        Long userId = testContext.getUserMap().get(username);

        // MySQL에 저장 확인
        UserEntity user = userJpaRepository.findById(userId).orElseThrow();
        assertThat(user.getFcmToken()).isNotNull();
        assertThat(user.getFcmToken()).startsWith("fcm_token_");

        // Redis 캐시 확인
        String cachedToken = redisTemplate.opsForValue().get("fcm:token:" + userId);
        assertThat(cachedToken).isNotNull();
        assertThat(cachedToken).isEqualTo(user.getFcmToken());
    }

    @Given("{string}이 기기 A에서 푸시 알림을 활성화했다")
    public void userActivatedPushOnDeviceA(String username) {
        Long userId = userFixture.createVisitorWithFcm(username, "device_a_token");
        testContext.getUserMap().put(username, userId);

        // Redis 캐시도 저장
        redisTemplate.opsForValue().set("fcm:token:" + userId, "device_a_token");
    }

    @When("{string}이 기기 B에서 토큰을 등록한다")
    public void userRegistersTokenOnDeviceB(String username) {
        Long userId = testContext.getUserMap().get(username);

        WebTestClient client = WebTestClient
                .bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();

        UpdateFcmTokenRequest request = new UpdateFcmTokenRequest("device_b_token");

        UpdateFcmTokenResponse response = client.post()
                .uri("/api/v1/users/fcm-token")
                .header("X-User-Id", userId.toString())
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(UpdateFcmTokenResponse.class)
                .returnResult()
                .getResponseBody();

        testContext.setUpdateFcmTokenResponse(response);
    }

    @Then("기기 B로 푸시 알림을 받을 수 있다")
    public void userCanReceivePushOnDeviceB() {
        // 응답 검증은 이미 완료
        assertThat(testContext.getUpdateFcmTokenResponse().success()).isTrue();
    }

    @And("기기 A로는 더 이상 푸시를 받지 않는다")
    public void deviceANoLongerReceivesPush() {
        // 기기 A의 토큰은 덮어씌워짐
        Long userId = testContext.getUserMap().get("user1");

        UserEntity user = userJpaRepository.findById(userId).orElseThrow();
        assertThat(user.getFcmToken()).isEqualTo("device_b_token");
        assertThat(user.getFcmToken()).isNotEqualTo("device_a_token");

        // Redis 캐시도 확인
        String cachedToken = redisTemplate.opsForValue().get("fcm:token:" + userId);
        assertThat(cachedToken).isEqualTo("device_b_token");
    }

    @Given("로그인하지 않은 상태이다")
    public void notLoggedIn() {
        // 아무것도 하지 않음 (존재하지 않는 userId 사용 예정)
    }

    @When("기기 토큰 등록을 시도한다")
    public void attemptToRegisterDeviceToken() {
        Long nonExistentUserId = 99999L;

        WebTestClient client = WebTestClient
                .bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();

        UpdateFcmTokenRequest request = new UpdateFcmTokenRequest("test_token");

        client.post()
                .uri("/api/v1/users/fcm-token")
                .header("X-User-Id", nonExistentUserId.toString())
                .bodyValue(request)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Then("인증 오류가 발생한다")
    public void authenticationErrorOccurs() {
        // expectStatus().isNotFound()로 이미 검증 완료
    }
}