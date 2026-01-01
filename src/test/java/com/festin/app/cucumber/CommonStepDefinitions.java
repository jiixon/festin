package com.festin.app.cucumber;

import com.festin.app.fixture.BoothFixture;
import com.festin.app.fixture.UserFixture;
import com.festin.app.university.adapter.out.persistence.entity.UniversityEntity;
import com.festin.app.university.adapter.out.persistence.repository.UniversityJpaRepository;
import io.cucumber.java.en.Given;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 공통 Given Step Definitions
 *
 * 여러 Feature에서 재사용되는 Given steps만 정의
 * Fixture 패턴 적용으로 코드 간소화
 * Fixture 사용해서 기본 엔티티 생성
 * TestContext에 ID 저장
 */
public class CommonStepDefinitions {

    @Autowired
    private UserFixture userFixture;

    @Autowired
    private BoothFixture boothFixture;

    @Autowired
    private UniversityJpaRepository universityRepository;

    @Autowired
    private TestContext testContext;

    @Given("애플리케이션이 실행중이다")
    public void applicationIsRunning() {
        // 테스트 환경이 이미 실행 중이므로 아무것도 하지 않음
    }

    @Given("테스트용 사용자 {string}이 존재한다")
    public void testUserExists(String username) {
        if (!testContext.getUserMap().containsKey(username)) {
            Long userId = userFixture.createVisitor(username);
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
        UniversityEntity university = universityRepository.findById(
                testContext.getUniversityMap().get(universityName)
        ).orElseThrow();

        Long boothId = boothFixture.createOpenBooth(university, boothName, capacity);
        testContext.getBoothMap().put(boothName, boothId);
    }
}
