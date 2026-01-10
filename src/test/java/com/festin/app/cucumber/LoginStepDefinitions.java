package com.festin.app.cucumber;

import com.festin.app.common.jwt.JwtTokenProvider;
import com.festin.app.user.adapter.in.web.dto.LoginRequest;
import com.festin.app.user.adapter.in.web.dto.LoginResponse;
import com.festin.app.user.adapter.out.persistence.repository.UserJpaRepository;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

public class LoginStepDefinitions {

    @LocalServerPort
    private int port;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private LoginResponse loginResponse;

    @When("사용자가 {string}, {string}, {string}로 로그인을 요청한다")
    public void userRequestsLogin(String email, String nickname, String role) {
        WebTestClient client = WebTestClient
                .bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();

        LoginRequest request = new LoginRequest(email, nickname, role, null);

        loginResponse = client.post()
                .uri("/api/v1/auth/login")
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(LoginResponse.class)
                .returnResult()
                .getResponseBody();
    }

    @Then("로그인이 성공하고 JWT 토큰을 받는다")
    public void loginSucceedsAndReceivesJwtToken() {
        assertThat(loginResponse).isNotNull();
        assertThat(loginResponse.accessToken()).isNotBlank();
    }

    @And("응답에 userId, email, nickname, role이 포함된다")
    public void responseContainsUserInformation() {
        assertThat(loginResponse.userId()).isNotNull();
        assertThat(loginResponse.email()).isNotBlank();
        assertThat(loginResponse.nickname()).isNotBlank();
        assertThat(loginResponse.role()).isNotBlank();
    }

    @And("{string}, {string}, {string}로 이미 가입된 사용자가 있다")
    public void existingUserExists(String email, String nickname, String role) {
        // 기존 사용자 생성 (먼저 로그인 요청)
        userRequestsLogin(email, nickname, role);
    }

    @And("닉네임이 {string}로 업데이트된다")
    public void nicknameIsUpdatedTo(String expectedNickname) {
        assertThat(loginResponse.nickname()).isEqualTo(expectedNickname);
    }

    @And("JWT 토큰에 userId, email, role 정보가 포함된다")
    public void jwtTokenContainsUserInformation() {
        String token = loginResponse.accessToken();

        Claims claims = jwtTokenProvider.validateAndGetClaims(token);

        assertThat(claims.getSubject()).isNotBlank();
        assertThat(claims.get("email", String.class)).isNotBlank();
        assertThat(claims.get("role", String.class)).isNotBlank();

        // 토큰의 userId와 응답의 userId가 일치하는지 확인
        Long tokenUserId = Long.parseLong(claims.getSubject());
        assertThat(tokenUserId).isEqualTo(loginResponse.userId());

        // 토큰의 email과 응답의 email이 일치하는지 확인
        String tokenEmail = claims.get("email", String.class);
        assertThat(tokenEmail).isEqualTo(loginResponse.email());

        // 토큰의 role과 응답의 role이 일치하는지 확인
        String tokenRole = claims.get("role", String.class);
        assertThat(tokenRole).isEqualTo(loginResponse.role());
    }
}
