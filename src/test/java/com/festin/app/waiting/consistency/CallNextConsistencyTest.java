package com.festin.app.waiting.consistency;

import com.festin.app.config.TestcontainersConfiguration;
import com.festin.app.fixture.BoothFixture;
import com.festin.app.fixture.UserFixture;
import com.festin.app.university.adapter.out.persistence.entity.UniversityEntity;
import com.festin.app.university.adapter.out.persistence.repository.UniversityJpaRepository;
import com.festin.app.waiting.application.port.in.CallNextUseCase;
import com.festin.app.waiting.application.port.out.QueueCachePort;
import com.festin.app.waiting.application.port.out.WaitingRepositoryPort;
import com.festin.app.waiting.domain.model.Waiting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * CallNext MySQL-Redis 정합성 테스트 (Soft Lock 방식)
 *
 * Issue #4 해결: Soft Lock으로 MySQL-Redis 정합성 보장
 * - Redis dequeue → Soft Lock 생성 → MySQL save
 * - MySQL 실패 시: Soft Lock 남김
 * - 배치가 Soft Lock 감지 → Redis 롤백
 * - 결과: 정합성 복구, 사용자 대기 유지
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CallNextConsistencyTest {

    @Autowired
    private CallNextUseCase callNextUseCase;

    @Autowired
    private UniversityJpaRepository universityRepository;

    @Autowired
    private UserFixture userFixture;

    @Autowired
    private BoothFixture boothFixture;

    @Autowired
    private QueueCachePort queueCachePort;

    @Autowired
    private WaitingRepositoryPort waitingRepositoryPort;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private Long testUniversityId;
    private Long testBoothId;
    private Long testUserId;

    @BeforeEach
    void setUp() {
        // Redis 초기화
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();

        // 대학교 생성
        String uniqueDomain = "TEST-UNIV-" + System.currentTimeMillis();
        UniversityEntity university = new UniversityEntity("테스트대학교", uniqueDomain);
        testUniversityId = universityRepository.save(university).getId();

        // 부스 생성
        UniversityEntity savedUniversity = universityRepository.findById(testUniversityId).orElseThrow();
        testBoothId = boothFixture.createOpenBooth(savedUniversity, "테스트 부스", 10);

        // 사용자 생성
        testUserId = userFixture.createVisitorWithFcm("테스트유저", "fcm-token-123");
    }

    @Test
    @DisplayName("[문제 재현] callNext 성공 시 Redis와 MySQL 모두 정상 반영")
    void callNext_Success_BothRedisAndMySQLUpdated() {
        // Given: 사용자가 대기열에 등록되어 있음
        LocalDateTime now = LocalDateTime.now();
        addUserToQueue(testBoothId, testUserId, now);

        // 초기 상태 확인
        assertThat(queueCachePort.getQueueSize(testBoothId)).isEqualTo(1);
        assertThat(queueCachePort.getUserActiveBoothCount(testUserId)).isEqualTo(1);

        // When: 다음 사람 호출
        var result = callNextUseCase.callNext(testBoothId);

        // Then: Redis 변경됨
        assertThat(queueCachePort.getQueueSize(testBoothId)).isEqualTo(0);
        assertThat(queueCachePort.getUserActiveBoothCount(testUserId)).isEqualTo(0);

        // MySQL에도 저장됨
        var savedWaiting = waitingRepositoryPort.findById(result.waitingId());
        assertThat(savedWaiting).isPresent();
        assertThat(savedWaiting.get().getUserId()).isEqualTo(testUserId);
        assertThat(savedWaiting.get().getStatus().name()).isEqualTo("CALLED");
    }

    @Test
    @DisplayName("[Soft Lock 방식] MySQL save 실패 → Soft Lock 남음 → 배치 복구 가능")
    void callNext_MySQLSaveFails_SoftLockCreated() {
        // Given: 사용자가 대기열에 등록되어 있음
        LocalDateTime now = LocalDateTime.now();
        addUserToQueue(testBoothId, testUserId, now);

        // 초기 상태 확인
        assertThat(queueCachePort.getQueueSize(testBoothId)).isEqualTo(1);
        assertThat(queueCachePort.getUserActiveBoothCount(testUserId)).isEqualTo(1);

        // MySQL save 실패 주입
        doThrow(new RuntimeException("MySQL connection timeout"))
                .when(waitingRepositoryPort).save(any(Waiting.class));

        // When: callNext 실행 → MySQL 실패로 예외 발생
        assertThatThrownBy(() -> callNextUseCase.callNext(testBoothId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("MySQL connection timeout");

        // Then: Soft Lock 방식으로 정합성 보장
        // 1. Redis는 dequeue됨 (사용자 제거)
        assertThat(queueCachePort.getQueueSize(testBoothId)).isEqualTo(0);
        assertThat(queueCachePort.getUserActiveBoothCount(testUserId)).isEqualTo(0);

        // 2. Soft Lock이 생성되어 있음 (복구 지점 마킹)
        var softLock = queueCachePort.getSoftLock(testBoothId, testUserId);
        assertThat(softLock).isPresent();
        assertThat(softLock.get().boothId()).isEqualTo(testBoothId);
        assertThat(softLock.get().userId()).isEqualTo(testUserId);

        // 3. MySQL에는 저장 안 됨 (트랜잭션 롤백)
        var savedWaiting = waitingRepositoryPort.findActiveWaitingsByUserId(testUserId);
        assertThat(savedWaiting).isEmpty();

        // 결론 (Soft Lock 방식):
        // ✅ Soft Lock이 timestamp를 보존하고 있음
        // ✅ 배치가 Soft Lock을 감지하여 Redis 롤백 가능
        // ✅ 사용자 대기 복구 가능!
    }

    /**
     * Port 레벨 Failure Injection 설정
     *
     * WaitingRepositoryPort를 Spy로 감싸서 특정 메서드만 실패 주입 가능
     * - Redis는 진짜 TestContainers
     * - MySQL도 진짜 동작 (대부분)
     * - save()만 선택적으로 실패 가능
     */
    @TestConfiguration
    static class FailureInjectionConfig {

        @Bean
        @Primary  // 기존 Bean Override
        public WaitingRepositoryPort spyWaitingRepository(WaitingRepositoryPort realRepository) {
            // 실제 구현체를 Spy로 감싸기
            // - 대부분의 메서드는 실제 동작
            // - doThrow()로 특정 메서드만 실패 주입 가능
            return Mockito.spy(realRepository);
        }
    }

    /**
     * 테스트 헬퍼: 사용자를 대기열에 추가
     */
    private void addUserToQueue(Long boothId, Long userId, LocalDateTime registeredAt) {
        String queueKey = "queue:booth:" + boothId;
        String activeBoothsKey = "user:" + userId + ":active_booths";

        double score = registeredAt.toEpochSecond(ZoneOffset.UTC);
        redisTemplate.opsForZSet().add(queueKey, userId.toString(), score);
        redisTemplate.opsForSet().add(activeBoothsKey, boothId.toString());
    }
}