package com.festin.app.cucumber;

import com.festin.app.booth.adapter.out.persistence.repository.BoothJpaRepository;
import com.festin.app.university.adapter.out.persistence.repository.UniversityJpaRepository;
import com.festin.app.user.adapter.out.persistence.repository.UserJpaRepository;
import com.festin.app.waiting.adapter.out.persistence.repository.WaitingJpaRepository;
import io.cucumber.java.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * Cucumber 전역 Hooks
 *
 * 모든 시나리오 실행 전 DB/Redis 초기화
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
        // DB 초기화
        waitingRepository.deleteAll();
        boothRepository.deleteAll();
        universityRepository.deleteAll();
        userRepository.deleteAll();

        // Redis 초기화
        redisTemplate.getConnectionFactory()
                .getConnection()
                .flushAll();

        // TestContext 초기화
        testContext.clearAll();

        // RabbitMQ Consumer 초기화
        testRabbitMQConsumer.reset();
    }
}