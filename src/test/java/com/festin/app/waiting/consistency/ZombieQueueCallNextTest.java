package com.festin.app.waiting.consistency;

import com.festin.app.config.TestcontainersConfiguration;
import com.festin.app.fixture.BoothFixture;
import com.festin.app.fixture.UserFixture;
import com.festin.app.university.adapter.out.persistence.entity.UniversityEntity;
import com.festin.app.university.adapter.out.persistence.repository.UniversityJpaRepository;
import com.festin.app.waiting.adapter.scheduler.SoftLockRecoveryBatch;
import com.festin.app.waiting.application.port.in.CallNextUseCase;
import com.festin.app.waiting.application.port.out.QueueCachePort;
import com.festin.app.waiting.application.port.out.WaitingRepositoryPort;
import com.festin.app.waiting.domain.exception.QueueEmptyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 좀비 대기자 방어 테스트
 *
 * 배경:
 * 대기열은 Redis, 사용자는 MySQL에 있어 둘 사이의 참조 무결성이 보장되지 않는다.
 * 사용자가 사라지면 대기열에 좀비 userId가 남는다.
 *
 * 방어 전 동작:
 * 1. 호출 → dequeue(좀비) → Soft Lock 생성
 * 2. Waiting INSERT → FK 위반 → 500
 * 3. 복구 배치가 원래 timestamp로 좀비를 되살림
 * 4. 좀비가 다시 큐 맨 앞 → 1번으로 (무한 루프, 부스 대기열 영구 정지)
 *
 * 방어 후 동작:
 * - 호출 시점: Soft Lock 생성 전에 사용자 존재를 확인하고 좀비는 폐기 후 다음 대기자로 진행
 * - 복구 배치: 좀비 Soft Lock은 롤백하지 않고 폐기하여 루프를 끊음
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ZombieQueueCallNextTest {

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
    private SoftLockRecoveryBatch softLockRecoveryBatch;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    /**
     * MySQL users 에 존재하지 않는 ID.
     * 실제 좀비는 사용자 삭제로 생기지만, 테스트에서는 미존재 ID로 동일 상태를 만든다.
     */
    private static final Long ZOMBIE_USER_ID = 999_999_999L;
    private static final Long ZOMBIE_USER_ID_2 = 999_999_998L;

    private Long testBoothId;
    private Long liveUserId;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();

        String uniqueDomain = "TEST-UNIV-" + System.currentTimeMillis();
        UniversityEntity university = universityRepository.save(
                new UniversityEntity("테스트대학교", uniqueDomain));

        testBoothId = boothFixture.createOpenBooth(university, "테스트 부스", 10);
        liveUserId = userFixture.createVisitorWithFcm("실유저", "fcm-token-live");
    }

    @Test
    @DisplayName("좀비가 큐 맨 앞에 있어도 뒤의 실유저가 정상 호출된다 — 부스가 멈추지 않음")
    void callNext_SkipsZombie_AndCallsNextLiveUser() {
        // Given: 좀비가 맨 앞, 실유저가 그 뒤
        LocalDateTime base = LocalDateTime.now();
        addUserToQueue(testBoothId, ZOMBIE_USER_ID, base);
        addUserToQueue(testBoothId, liveUserId, base.plusSeconds(10));

        assertThat(queueCachePort.getQueueSize(testBoothId)).isEqualTo(2);

        // When: 다음 사람 호출
        var result = callNextUseCase.callNext(testBoothId);

        // Then: 좀비를 건너뛰고 실유저가 호출됨
        assertThat(result.userId()).isEqualTo(liveUserId);

        // 좀비와 실유저 모두 큐에서 제거됨
        assertThat(queueCachePort.getQueueSize(testBoothId)).isZero();

        // MySQL 에는 실유저만 CALLED 로 저장됨
        var savedWaiting = waitingRepositoryPort.findById(result.waitingId());
        assertThat(savedWaiting).isPresent();
        assertThat(savedWaiting.get().getUserId()).isEqualTo(liveUserId);
        assertThat(savedWaiting.get().getStatus().name()).isEqualTo("CALLED");
    }

    @Test
    @DisplayName("좀비는 Soft Lock 을 남기지 않는다 — 복구 배치가 되살릴 수 없음")
    void callNext_ZombieLeavesNoSoftLock() {
        // Given: 좀비 2명이 맨 앞, 실유저가 뒤
        LocalDateTime base = LocalDateTime.now();
        addUserToQueue(testBoothId, ZOMBIE_USER_ID, base);
        addUserToQueue(testBoothId, ZOMBIE_USER_ID_2, base.plusSeconds(5));
        addUserToQueue(testBoothId, liveUserId, base.plusSeconds(10));

        // When
        callNextUseCase.callNext(testBoothId);

        // Then: 좀비 Soft Lock 이 하나도 생성되지 않음
        assertThat(queueCachePort.getSoftLock(testBoothId, ZOMBIE_USER_ID)).isEmpty();
        assertThat(queueCachePort.getSoftLock(testBoothId, ZOMBIE_USER_ID_2)).isEmpty();

        // 좀비의 활성 부스 목록도 정리됨
        assertThat(queueCachePort.getUserActiveBoothCount(ZOMBIE_USER_ID)).isZero();
        assertThat(queueCachePort.getUserActiveBoothCount(ZOMBIE_USER_ID_2)).isZero();
    }

    @Test
    @DisplayName("좀비를 폐기한 뒤에도 대기열이 되살아나지 않는다 — 복구 배치 실행 후 검증")
    void recoveryBatch_DoesNotRevive_DiscardedZombies() {
        // Given: 좀비만 있는 대기열
        LocalDateTime base = LocalDateTime.now();
        addUserToQueue(testBoothId, ZOMBIE_USER_ID, base);

        // When: 호출 → 좀비 폐기 후 큐가 비어 QueueEmptyException
        assertThatThrownBy(() -> callNextUseCase.callNext(testBoothId))
                .isInstanceOf(QueueEmptyException.class);

        // 복구 배치 실행
        softLockRecoveryBatch.recoverSoftLockFailures();

        // Then: 좀비가 대기열로 돌아오지 않음 (무한 루프 차단)
        assertThat(queueCachePort.getQueueSize(testBoothId)).isZero();
        assertThat(queueCachePort.getSoftLock(testBoothId, ZOMBIE_USER_ID)).isEmpty();
    }

    @Test
    @DisplayName("[2차 방어] 좀비 Soft Lock 이 남아 있어도 복구 배치가 롤백하지 않는다")
    void recoveryBatch_DiscardsZombieSoftLock_WithoutRollback() {
        // Given: 호출 도중 사용자가 사라진 상황을 재현
        //        (Soft Lock 은 만들어졌지만 그 사이 users 행이 없어진 경우)
        LocalDateTime registeredAt = LocalDateTime.now();
        queueCachePort.createSoftLock(testBoothId, ZOMBIE_USER_ID, registeredAt);

        assertThat(queueCachePort.getSoftLock(testBoothId, ZOMBIE_USER_ID)).isPresent();
        assertThat(queueCachePort.getQueueSize(testBoothId)).isZero();

        // When: 복구 배치 실행
        softLockRecoveryBatch.recoverSoftLockFailures();

        // Then: 롤백하지 않고 Soft Lock 만 제거 — 좀비가 큐로 복원되지 않음
        assertThat(queueCachePort.getSoftLock(testBoothId, ZOMBIE_USER_ID)).isEmpty();
        assertThat(queueCachePort.getQueueSize(testBoothId)).isZero();
        assertThat(queueCachePort.getUserActiveBoothCount(ZOMBIE_USER_ID)).isZero();
    }

    @Test
    @DisplayName("[대조군] 실유저의 Soft Lock 은 기존대로 원래 순번으로 복구된다")
    void recoveryBatch_StillRevives_LiveUserSoftLock() {
        // Given: 실유저의 Soft Lock (MySQL save 실패 상황)
        LocalDateTime registeredAt = LocalDateTime.now().minusMinutes(3);
        queueCachePort.createSoftLock(testBoothId, liveUserId, registeredAt);

        // When: 복구 배치 실행
        softLockRecoveryBatch.recoverSoftLockFailures();

        // Then: 대기열로 복원됨 (Soft Lock 본래 동작 유지)
        assertThat(queueCachePort.getQueueSize(testBoothId)).isEqualTo(1);
        assertThat(queueCachePort.getPosition(testBoothId, liveUserId)).contains(1);
        assertThat(queueCachePort.getSoftLock(testBoothId, liveUserId)).isEmpty();
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