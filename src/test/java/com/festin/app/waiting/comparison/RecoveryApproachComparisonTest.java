//package com.festin.app.waiting.comparison;
//
//import com.festin.app.config.TestcontainersConfiguration;
//import com.festin.app.fixture.BoothFixture;
//import com.festin.app.fixture.UserFixture;
//import com.festin.app.university.adapter.out.persistence.entity.UniversityEntity;
//import com.festin.app.university.adapter.out.persistence.repository.UniversityJpaRepository;
//import com.festin.app.waiting.adapter.scheduler.EventListenerRecoveryBatch;
//import com.festin.app.waiting.adapter.scheduler.SoftLockRecoveryBatch;
//import com.festin.app.waiting.application.port.in.CallNextUseCase;
//import com.festin.app.waiting.application.port.out.QueueCachePort;
//import com.festin.app.waiting.application.port.out.WaitingRepositoryPort;
//import com.festin.app.waiting.application.service.CallNextServiceV2EventListener;
//import com.festin.app.waiting.application.service.CallNextServiceV3SoftLock;
//import com.festin.app.waiting.domain.model.Waiting;
//import com.festin.app.waiting.domain.model.WaitingStatus;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.mockito.Mockito;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.boot.test.context.TestConfiguration;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Import;
//import org.springframework.context.annotation.Primary;
//import org.springframework.dao.DataAccessException;
//import org.springframework.data.redis.core.RedisTemplate;
//import org.springframework.test.context.ActiveProfiles;
//
//import java.time.LocalDateTime;
//import java.time.ZoneOffset;
//import java.util.List;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.assertj.core.api.Assertions.assertThatThrownBy;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.Mockito.doThrow;
//
///**
// * TransactionalEventListener vs Soft Lock 정량적 비교 테스트
// *
// * 측정 지표:
// * 1. 복구 정확성: 원래 rank vs 복구 후 rank 차이
// * 2. 배치 스캔 효율: 스캔 시간 및 조회 건수
// * 3. 복구 시간: 실패 → 복구 완료까지 소요 시간
// *
// * 블로그 결론 근거 데이터 수집
// */
//@SpringBootTest
//@ActiveProfiles("test")
//@Import(TestcontainersConfiguration.class)
//class RecoveryApproachComparisonTest {
//
//    @Autowired
//    private CallNextServiceV2EventListener eventListenerService;
//
//    @Autowired
//    private CallNextServiceV3SoftLock softLockService;
//
//    @Autowired
//    private EventListenerRecoveryBatch eventListenerBatch;
//
//    @Autowired
//    private SoftLockRecoveryBatch softLockBatch;
//
//    @Autowired
//    private UniversityJpaRepository universityRepository;
//
//    @Autowired
//    private UserFixture userFixture;
//
//    @Autowired
//    private BoothFixture boothFixture;
//
//    @Autowired
//    private QueueCachePort queueCachePort;
//
//    @Autowired
//    private WaitingRepositoryPort waitingRepositoryPort;
//
//    @Autowired
//    private RedisTemplate<String, String> redisTemplate;
//
//    private Long testUniversityId;
//    private Long testBoothId;
//
//    @BeforeEach
//    void setUp() {
//        // Redis 초기화
//        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
//
//        // Spy 리셋 (이전 테스트의 stub 제거)
//        Mockito.reset(waitingRepositoryPort);
//
//        // 대학교 생성
//        String uniqueDomain = "TEST-UNIV-" + System.currentTimeMillis();
//        UniversityEntity university = new UniversityEntity("테스트대학교", uniqueDomain);
//        testUniversityId = universityRepository.save(university).getId();
//
//        // 부스 생성
//        UniversityEntity savedUniversity = universityRepository.findById(testUniversityId).orElseThrow();
//        testBoothId = boothFixture.createOpenBooth(savedUniversity, "테스트 부스", 10);
//    }
//
//    @Test
//    @DisplayName("[측정 1] 복구 정확성: 원래 순서 보존 - Soft Lock 승리")
//    void recoveryAccuracy_SoftLockPreservesOriginalRank() {
//        // Given: 대기열에 5명 등록 (rank: 1, 2, 3, 4, 5)
//        Long user1 = userFixture.createVisitorWithFcm("유저1", "fcm1");
//        Long user2 = userFixture.createVisitorWithFcm("유저2", "fcm2");
//        Long user3 = userFixture.createVisitorWithFcm("유저3", "fcm3"); // 이 사용자를 callNext 시도
//        Long user4 = userFixture.createVisitorWithFcm("유저4", "fcm4");
//        Long user5 = userFixture.createVisitorWithFcm("유저5", "fcm5");
//
//        LocalDateTime now = LocalDateTime.now();
//        addUserToQueue(testBoothId, user1, now.minusSeconds(4));  // rank 1
//        addUserToQueue(testBoothId, user2, now.minusSeconds(3));  // rank 2
//        addUserToQueue(testBoothId, user3, now.minusSeconds(2));  // rank 3 ← callNext 대상
//        addUserToQueue(testBoothId, user4, now.minusSeconds(1));  // rank 4
//        addUserToQueue(testBoothId, user5, now);                  // rank 5
//
//        // user3의 원래 rank 확인
//        int originalRank = queueCachePort.getPosition(testBoothId, user3).orElse(-1);
//        assertThat(originalRank).isEqualTo(3);
//
//        // 2명 callNext 성공 (user1, user2 제거)
//        queueCachePort.dequeue(testBoothId); // user1
//        queueCachePort.dequeue(testBoothId); // user2
//
//        // user3가 이제 rank 1
//        int currentRank = queueCachePort.getPosition(testBoothId, user3).orElse(-1);
//        assertThat(currentRank).isEqualTo(1);
//
//        // user3 callNext 시도 → MySQL 실패
//        doThrow(new RuntimeException("MySQL failure"))
//                .when(waitingRepositoryPort).save(any(Waiting.class));
//
//        // Soft Lock 방식: dequeue → MySQL 실패 → Soft Lock 남김
//        assertThatThrownBy(() -> softLockService.callNext(testBoothId))
//                .isInstanceOf(RuntimeException.class);
//
//        // user3 Redis에서 제거됨 (Soft Lock 남아있음)
//        int afterFailureRank = queueCachePort.getPosition(testBoothId, user3).orElse(-1);
//        assertThat(afterFailureRank).isEqualTo(-1); // 대기열에 없음
//
//        // When: 배치 보정 실행
//        softLockBatch.recoverSoftLockFailures();
//
//        // Then: 원래 rank로 복구 (rank 1)
//        int recoveredRank = queueCachePort.getPosition(testBoothId, user3).orElse(-1);
//        assertThat(recoveredRank).isEqualTo(1); // ✅ 정확히 원래 위치 (제거 직전 rank)
//
//        // 결론: Soft Lock은 dequeue 시점의 timestamp를 보존하므로 정확한 위치 복구
//        System.out.println("✅ [Soft Lock] 복구 정확성: 원래 rank 3 → dequeue 후 rank 1 → 복구 후 rank " + recoveredRank);
//    }
//
//    @Test
//    @DisplayName("[측정 2] 배치 스캔 효율: Soft Lock만 스캔 vs CALLED 전체 조회")
//    void batchScanEfficiency_SoftLockScansOnlyFailures() {
//        // Given: 100명이 호출됨 (CALLED 상태, 최근 3분 이내)
//        LocalDateTime now = LocalDateTime.now();
//        for (int i = 0; i < 100; i++) {
//            Long userId = userFixture.createVisitorWithFcm("유저" + i, "fcm" + i);
//            Waiting waiting = Waiting.ofCalled(
//                    userId,
//                    testBoothId,
//                    i + 1,
//                    now.minusMinutes(10),  // 등록 시간
//                    now.minusMinutes(3)    // ← 호출 시간: 3분 전 (배치가 조회할 수 있도록)
//            );
//            waitingRepositoryPort.save(waiting); // MySQL에 100건 저장 (성공)
//        }
//
//        // 1건만 실패 케이스 생성
//        Long failedUserId = userFixture.createVisitorWithFcm("실패유저", "fcm-failed");
//        addUserToQueue(testBoothId, failedUserId, LocalDateTime.now());
//
//        // ⚠️ 이제 실패 주입 (100건 저장 후에)
//        doThrow(new RuntimeException("MySQL failure"))
//                .when(waitingRepositoryPort).save(any(Waiting.class));
//
//        // 실패 케이스 발생
//        assertThatThrownBy(() -> softLockService.callNext(testBoothId))
//                .isInstanceOf(RuntimeException.class);
//
//        // When: 배치 실행 및 시간 측정
//        Mockito.clearInvocations(waitingRepositoryPort);
//
//        long softLockStart = System.currentTimeMillis();
//        softLockBatch.recoverSoftLockFailures();
//        long softLockDuration = System.currentTimeMillis() - softLockStart;
//
//        long eventListenerStart = System.currentTimeMillis();
//        eventListenerBatch.recoverAfterCommitFailures();
//        long eventListenerDuration = System.currentTimeMillis() - eventListenerStart;
//
//        // Then: 효율성 비교
//        System.out.println("\n========== 배치 스캔 효율 비교 ==========");
//        System.out.println("[Soft Lock] 스캔 시간: " + softLockDuration + "ms (Soft Lock만 스캔)");
//        System.out.println("[EventListener] 스캔 시간: " + eventListenerDuration + "ms (CALLED 100건 조회)");
//        System.out.println("========================================\n");
//
//        // Soft Lock: Redis SCAN temp:calling:* (실패 건수만큼)
//        // EventListener: MySQL SELECT WHERE status=CALLED AND calledAt > now-5min (100건)
//        // 결론: Soft Lock이 더 효율적
//    }
//
//    @Test
//    @DisplayName("[측정 3] 복구 시간: 실패 감지부터 복구 완료까지")
//    void recoveryTime_SoftLockFasterDetection() {
//        // Given: 실패 케이스 생성
//        Long userId = userFixture.createVisitorWithFcm("테스트유저", "fcm-test");
//        LocalDateTime registeredAt = LocalDateTime.now();
//        addUserToQueue(testBoothId, userId, registeredAt);
//
//        // MySQL 실패 주입
//        doThrow(new RuntimeException("MySQL failure"))
//                .when(waitingRepositoryPort).save(any(Waiting.class));
//
//        long failureTime = System.currentTimeMillis();
//
//        // When: callNext 실패
//        assertThatThrownBy(() -> softLockService.callNext(testBoothId))
//                .isInstanceOf(RuntimeException.class);
//
//        // 배치 실행 (실전에서는 1분 후 스케줄러가 실행)
//        long batchStartTime = System.currentTimeMillis();
//        softLockBatch.recoverSoftLockFailures();
//        long recoveryCompleteTime = System.currentTimeMillis();
//
//        // Then: 복구 시간 측정
//        long totalRecoveryTime = recoveryCompleteTime - failureTime;
//        long batchProcessingTime = recoveryCompleteTime - batchStartTime;
//
//        System.out.println("\n========== 복구 시간 측정 (Soft Lock) ==========");
//        System.out.println("실패 발생 → 배치 시작: " + (batchStartTime - failureTime) + "ms (즉시)");
//        System.out.println("배치 처리 시간: " + batchProcessingTime + "ms");
//        System.out.println("총 복구 시간: " + totalRecoveryTime + "ms");
//        System.out.println("===============================================\n");
//
//        // 복구 확인
//        int recoveredRank = queueCachePort.getPosition(testBoothId, userId).orElse(-1);
//        assertThat(recoveredRank).isEqualTo(1); // 복구 완료
//
//        // 결론:
//        // - Soft Lock: 즉시 감지 가능 (Soft Lock 존재 = 실패)
//        // - EventListener: MySQL 조회 필요 (CALLED 있는데 Redis에도 있는 케이스 탐색)
//    }
//
//    /**
//     * Port 레벨 Failure Injection 설정
//     */
//    @TestConfiguration
//    static class FailureInjectionConfig {
//
//        @Bean
//        @Primary
//        public WaitingRepositoryPort spyWaitingRepository(WaitingRepositoryPort realRepository) {
//            return Mockito.spy(realRepository);
//        }
//    }
//
//    /**
//     * 테스트 헬퍼: 사용자를 대기열에 추가
//     */
//    private void addUserToQueue(Long boothId, Long userId, LocalDateTime registeredAt) {
//        String queueKey = "queue:booth:" + boothId;
//        String activeBoothsKey = "user:" + userId + ":active_booths";
//
//        double score = registeredAt.toEpochSecond(ZoneOffset.UTC);
//        redisTemplate.opsForZSet().add(queueKey, userId.toString(), score);
//        redisTemplate.opsForSet().add(activeBoothsKey, boothId.toString());
//    }
//}