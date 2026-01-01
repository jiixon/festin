package com.festin.app.cucumber;

import com.festin.app.fixture.UserFixture;
import com.festin.app.fixture.WaitingFixtureBuilder;
import com.festin.app.waiting.adapter.in.web.dto.CalledListResponse;
import com.festin.app.waiting.domain.model.CompletionType;
import com.festin.app.waiting.domain.model.WaitingStatus;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 호출 대기 목록 조회 Step Definitions
 *
 * Fixture Builder 패턴 사용:
 * - 과거 시간 조작이 필요한 데이터는 Fixture 사용
 * - 복잡한 상태 (ENTERED, COMPLETED)는 Fixture 사용
 */
public class CalledListStepDefinitions {

    @LocalServerPort
    private int port;

    @Autowired
    private WaitingFixtureBuilder waitingFixtureBuilder;

    @Autowired
    private UserFixture userFixture;

    @Autowired
    private TestContext testContext;

    /**
     * Fixture 방식: 과거 시간 조작
     */
    @And("{string}이 {string}에 호출되었다 \\({int}분 전)")
    @And("{string}가 {string}에 호출되었다 \\({int}분 전)")
    public void userWasCalledMinutesAgo(String nickname, String boothName, int minutesAgo) {
        Long userId = userFixture.createVisitor(nickname);
        Long boothId = testContext.getBoothMap().get(boothName);

        waitingFixtureBuilder
                .forUser(userId)
                .forBooth(boothId)
                .position(1)
                .statusCalled(LocalDateTime.now().minusMinutes(minutesAgo))
                .build();
    }

    /**
     * Fixture 방식: 복잡한 상태 (ENTERED)
     */
    @And("{string}이 {string}에 입장했다")
    @And("{string}가 {string}에 입장했다")
    public void userEntered(String nickname, String boothName) {
        Long userId = userFixture.createVisitor(nickname);
        Long boothId = testContext.getBoothMap().get(boothName);

        waitingFixtureBuilder
                .forUser(userId)
                .forBooth(boothId)
                .position(2)
                .statusEntered(
                        LocalDateTime.now().minusMinutes(10),
                        LocalDateTime.now().minusMinutes(5)
                )
                .build();
    }

    /**
     * Fixture 방식: 복잡한 상태 (COMPLETED)
     */
    @And("{string}이 {string}에서 체험을 완료했다")
    public void userCompleted(String nickname, String boothName) {
        Long userId = userFixture.createVisitor(nickname);
        Long boothId = testContext.getBoothMap().get(boothName);

        waitingFixtureBuilder
                .forUser(userId)
                .forBooth(boothId)
                .position(3)
                .statusCompleted(
                        CompletionType.ENTERED,
                        LocalDateTime.now().minusHours(1),
                        LocalDateTime.now().minusMinutes(50),
                        LocalDateTime.now().minusMinutes(40)
                )
                .build();
    }

    @When("스태프가 {string}의 호출 대기 목록을 조회한다")
    public void staffGetsCalledList(String boothName) {
        WebTestClient webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();

        Long boothId = testContext.getBoothMap().get(boothName);

        CalledListResponse response = webTestClient.get()
                .uri("/api/v1/booths/" + boothId + "/called-list")
                .exchange()
                .expectStatus().isOk()
                .expectBody(CalledListResponse.class)
                .returnResult()
                .getResponseBody();

        testContext.setCalledListResponse(response);
    }

    @Then("호출 대기 목록은 비어있다")
    public void calledListIsEmpty() {
        assertThat(testContext.getCalledListResponse().calledList()).isEmpty();
    }

    @Then("호출 대기 목록 크기는 {int}이다")
    public void calledListSizeIs(int size) {
        assertThat(testContext.getCalledListResponse().calledList()).hasSize(size);
    }

    @And("첫 번째 항목의 닉네임은 {string}이다")
    public void firstItemNicknameIs(String expectedNickname) {
        String actualNickname = testContext.getCalledListResponse().calledList().get(0).nickname();
        assertThat(actualNickname).isEqualTo(expectedNickname);
    }

    @And("첫 번째 항목의 상태는 {string}이다")
    public void firstItemStatusIs(String expectedStatus) {
        WaitingStatus actualStatus = testContext.getCalledListResponse().calledList().get(0).status();
        assertThat(actualStatus).isEqualTo(WaitingStatus.valueOf(expectedStatus));
    }

    @And("첫 번째 항목의 남은 시간은 약 {int}초이다")
    public void firstItemRemainingTimeIsAbout(int expectedSeconds) {
        int actual = testContext.getCalledListResponse().calledList().get(0).remainingTime();
        // ±10초 허용 (API 호출 지연 고려)
        assertThat(actual).isBetween(expectedSeconds - 10, expectedSeconds + 10);
    }

    @And("첫 번째 항목의 남은 시간은 {int}초이다")
    public void firstItemRemainingTimeIs(int expectedSeconds) {
        int actual = testContext.getCalledListResponse().calledList().get(0).remainingTime();
        assertThat(actual).isEqualTo(expectedSeconds);
    }

    @And("세 번째 항목의 남은 시간은 약 {int}초이다")
    public void thirdItemRemainingTimeIsAbout(int expectedSeconds) {
        int actual = testContext.getCalledListResponse().calledList().get(2).remainingTime();
        // ±10초 허용 (API 호출 지연 고려)
        assertThat(actual).isBetween(expectedSeconds - 10, expectedSeconds + 10);
    }

    @And("호출 목록은 시간 순으로 정렬되어 있다")
    public void calledListIsSortedByTime() {
        // calledAt 순서대로 정렬되어 있는지 검증
        var list = testContext.getCalledListResponse().calledList();
        for (int i = 1; i < list.size(); i++) {
            String prevCalledAt = list.get(i - 1).calledAt();
            String currCalledAt = list.get(i).calledAt();
            assertThat(prevCalledAt).isLessThanOrEqualTo(currCalledAt);
        }
    }
}
