package com.festin.app.cucumber;

import com.festin.app.booth.adapter.out.persistence.entity.BoothEntity;
import com.festin.app.booth.adapter.out.persistence.repository.BoothJpaRepository;
import com.festin.app.booth.domain.model.BoothStatus;
import com.festin.app.university.adapter.out.persistence.entity.UniversityEntity;
import com.festin.app.university.adapter.out.persistence.repository.UniversityJpaRepository;
import com.festin.app.user.adapter.out.persistence.entity.UserEntity;
import com.festin.app.user.adapter.out.persistence.repository.UserJpaRepository;
import com.festin.app.user.domain.model.Role;
import io.cucumber.java.en.Given;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * 공통 Given Step Definitions
 *
 * 여러 Feature에서 재사용되는 Given steps만 정의
 */
public class CommonStepDefinitions {

    @Autowired
    private UserJpaRepository userRepository;

    @Autowired
    private UniversityJpaRepository universityRepository;

    @Autowired
    private BoothJpaRepository boothRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private TestContext testContext;

    @Given("애플리케이션이 실행중이다")
    public void applicationIsRunning() {
        // 테스트 환경이 이미 실행 중이므로 아무것도 하지 않음
    }

    @Given("테스트용 사용자 {string}이 존재한다")
    public void testUserExists(String username) {
        if (!testContext.getUserMap().containsKey(username)) {
            String uniqueEmail = username + "-" + System.currentTimeMillis() + "@test.com";
            UserEntity user = new UserEntity(uniqueEmail, username, Role.VISITOR);
            Long userId = userRepository.save(user).getId();
            testContext.getUserMap().put(username, userId);
        }
    }

    @Given("테스트용 대학교 {string}가 존재한다")
    public void testUniversityExists(String universityName) {
        if (!testContext.getUniversityMap().containsKey(universityName)) {
            String uniqueDomain = universityName + "-" + System.currentTimeMillis();
            UniversityEntity university = new UniversityEntity(universityName, uniqueDomain);
            Long universityId = universityRepository.save(university).getId();
            testContext.getUniversityMap().put(universityName, universityId);
        }
    }

    @Given("{string}에 정원 {int}명인 {string} 부스가 존재한다")
    public void boothWithCapacityExists(String universityName, int capacity, String boothName) {
        UniversityEntity university = universityRepository.findById(testContext.getUniversityMap().get(universityName))
                .orElseThrow();

        BoothEntity booth = new BoothEntity(
                university,
                boothName,
                boothName + " 설명",
                capacity,
                BoothStatus.OPEN
        );

        Long boothId = boothRepository.save(booth).getId();
        testContext.getBoothMap().put(boothName, boothId);

        // Redis에 부스 메타 정보 저장
        String metaKey = "booth:" + boothId + ":meta";
        redisTemplate.opsForHash().put(metaKey, "status", "OPEN");
        redisTemplate.opsForHash().put(metaKey, "name", boothName);
        redisTemplate.opsForHash().put(metaKey, "capacity", String.valueOf(capacity));
    }
}
