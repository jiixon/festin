package com.festin.app.cucumber;

import com.festin.app.booth.adapter.in.web.dto.BoothListResponse;
import com.festin.app.booth.adapter.out.persistence.entity.BoothEntity;
import com.festin.app.booth.adapter.out.persistence.repository.BoothJpaRepository;
import com.festin.app.booth.domain.model.BoothStatus;
import com.festin.app.university.adapter.out.persistence.entity.UniversityEntity;
import com.festin.app.university.adapter.out.persistence.repository.UniversityJpaRepository;
import com.festin.app.user.adapter.out.persistence.entity.UserEntity;
import com.festin.app.user.adapter.out.persistence.repository.UserJpaRepository;
import com.festin.app.user.domain.model.Role;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class BoothListStepDefinitions {

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
    private Map<String, Long> universityMap;
    private Map<String, Long> boothMap;
    private BoothListResponse boothListResponse;

    @Before
    public void setup() {
        boothRepository.deleteAll();
        universityRepository.deleteAll();
        userRepository.deleteAll();

        webTestClient = WebTestClient
                .bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();

        redisTemplate.getConnectionFactory()
                .getConnection()
                .flushAll();

        universityMap = new HashMap<>();
        boothMap = new HashMap<>();
    }

    @And("테스트용 대학교 {string}가 존재한다")
    public void testUniversityExists(String universityName) {
        if (!universityMap.containsKey(universityName)) {
            String uniqueDomain = universityName + "-" + System.currentTimeMillis();
            UniversityEntity university = new UniversityEntity(universityName, uniqueDomain);
            Long universityId = universityRepository.save(university).getId();
            universityMap.put(universityName, universityId);
        }
    }

    @And("{string}에 {string} 부스가 존재한다")
    public void boothExistsInUniversity(String universityName, String boothName) {
        UniversityEntity university = universityRepository.findById(universityMap.get(universityName))
                .orElseThrow();

        BoothEntity booth = new BoothEntity(
                university,
                boothName,
                boothName + " 설명",
                10,
                BoothStatus.OPEN
        );

        Long boothId = boothRepository.save(booth).getId();
        boothMap.put(boothName, boothId);

        // Redis에 부스 메타 정보 저장
        String metaKey = "booth:" + boothId + ":meta";
        redisTemplate.opsForHash().put(metaKey, "status", "OPEN");
        redisTemplate.opsForHash().put(metaKey, "name", boothName);
        redisTemplate.opsForHash().put(metaKey, "capacity", "10");
    }

    @And("{string}에 정원 {int}명인 {string} 부스가 존재한다")
    public void boothWithCapacityExistsInUniversity(String universityName, int capacity, String boothName) {
        UniversityEntity university = universityRepository.findById(universityMap.get(universityName))
                .orElseThrow();

        BoothEntity booth = new BoothEntity(
                university,
                boothName,
                boothName + " 설명",
                capacity,
                BoothStatus.OPEN
        );

        Long boothId = boothRepository.save(booth).getId();
        boothMap.put(boothName, boothId);

        // Redis에 부스 메타 정보 저장
        String metaKey = "booth:" + boothId + ":meta";
        redisTemplate.opsForHash().put(metaKey, "status", "OPEN");
        redisTemplate.opsForHash().put(metaKey, "name", boothName);
        redisTemplate.opsForHash().put(metaKey, "capacity", String.valueOf(capacity));
    }

    @And("{string}에 {int}명의 대기자가 있다")
    public void waitingUsersExistInBooth(String boothName, int waitingCount) {
        Long boothId = boothMap.get(boothName);
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
        boothListResponse = webTestClient.get()
                .uri("/api/v1/booths")
                .exchange()
                .expectStatus().isOk()
                .expectBody(BoothListResponse.class)
                .returnResult()
                .getResponseBody();
    }

    @When("{string}의 부스 목록을 조회한다")
    public void getBoothListByUniversity(String universityName) {
        Long universityId = universityMap.get(universityName);

        boothListResponse = webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/booths")
                        .queryParam("universityId", universityId)
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(BoothListResponse.class)
                .returnResult()
                .getResponseBody();
    }

    @Then("응답에 {int}개의 부스가 포함된다")
    public void responseContainsBooths(int expectedCount) {
        assertThat(boothListResponse.booths()).hasSize(expectedCount);
    }

    @And("부스 목록에 {string}가 포함된다")
    public void boothListContainsBooth(String boothName) {
        boolean exists = boothListResponse.booths().stream()
                .anyMatch(booth -> booth.boothName().equals(boothName));
        assertThat(exists).isTrue();
    }

    @Then("{string}의 대기 인원은 {int}명이다")
    public void boothHasWaitingCount(String boothName, int expectedWaiting) {
        BoothListResponse.BoothItem booth = boothListResponse.booths().stream()
                .filter(b -> b.boothName().equals(boothName))
                .findFirst()
                .orElseThrow();

        assertThat(booth.currentWaiting()).isEqualTo(expectedWaiting);
    }

    @And("{string}의 예상 대기 시간은 {int}분이다")
    public void boothHasEstimatedWaitTime(String boothName, int expectedTime) {
        BoothListResponse.BoothItem booth = boothListResponse.booths().stream()
                .filter(b -> b.boothName().equals(boothName))
                .findFirst()
                .orElseThrow();

        assertThat(booth.estimatedWaitTime()).isEqualTo(expectedTime);
    }
}