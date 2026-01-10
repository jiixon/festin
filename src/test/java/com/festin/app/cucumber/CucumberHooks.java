package com.festin.app.cucumber;

import com.festin.app.booth.adapter.out.persistence.repository.BoothJpaRepository;
import com.festin.app.university.adapter.out.persistence.repository.UniversityJpaRepository;
import com.festin.app.user.adapter.out.persistence.repository.UserJpaRepository;
import com.festin.app.waiting.adapter.out.persistence.repository.WaitingJpaRepository;
import io.cucumber.java.After;
import io.cucumber.java.AfterAll;
import io.cucumber.java.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * Cucumber 전역 Hooks
 *
 * 모든 시나리오 실행 전/후 DB/Redis 초기화
 */
public class CucumberHooks {

    @Autowired
    private UserJpaRepository userRepository;

    @Autowired
    private UniversityJpaRepository universityRepository;

    @Autowired
    private BoothJpaRepository boothRepository;

    @Autowired
    private WaitingJpaRepository waitingRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private TestContext testContext;

    @Autowired
    private TestRabbitMQConsumer testRabbitMQConsumer;

    /**
     * 각 시나리오 실행 전 초기화
     */
    @Before
    public void beforeScenario() {
        cleanupData();
    }

    /**
     * 각 시나리오 실행 후 정리
     */
    @After
    public void afterScenario() {
        cleanupData();
    }

    /**
     * 모든 테스트 종료 후 최종 정리
     */
    @AfterAll
    public static void afterAllTests() {
        System.out.println("모든 테스트 완료 - 테스트 DB와 Redis는 다음 테스트 시작 시 자동 정리됩니다.");
    }

    private void cleanupData() {
        // DB 초기화
        waitingRepository.deleteAll();
        boothRepository.deleteAll();
        universityRepository.deleteAll();
        userRepository.deleteAll();

        // Redis 초기화 (테스트용 DB만 삭제)
        redisTemplate.getConnectionFactory()
                .getConnection()
                .serverCommands()
                .flushDb();

        // TestContext 초기화
        testContext.clearAll();

        // RabbitMQ Consumer 초기화
        testRabbitMQConsumer.reset();
    }
}