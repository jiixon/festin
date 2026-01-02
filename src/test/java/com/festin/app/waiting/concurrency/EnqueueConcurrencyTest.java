package com.festin.app.waiting.concurrency;

import com.festin.app.fixture.BoothFixture;
import com.festin.app.fixture.UserFixture;
import com.festin.app.university.adapter.out.persistence.entity.UniversityEntity;
import com.festin.app.university.adapter.out.persistence.repository.UniversityJpaRepository;
import com.festin.app.waiting.application.port.in.EnqueueUseCase;
import com.festin.app.waiting.application.port.in.command.EnqueueCommand;
import com.festin.app.waiting.application.port.out.QueueCachePort;
import com.festin.app.waiting.domain.exception.MaxWaitingExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 대기 등록 동시성 테스트
 *
 * Race Condition 방지 검증:
 * - Lua Script가 없으면: 3개 부스 모두 등록 가능 (버그)
 * - Lua Script가 있으면: 2개만 성공, 1개 실패 (정상)
 */
@SpringBootTest
@ActiveProfiles("test")
class EnqueueConcurrencyTest {

    @Autowired
    private EnqueueUseCase enqueueUseCase;

    @Autowired
    private UniversityJpaRepository universityRepository;

    @Autowired
    private UserFixture userFixture;

    @Autowired
    private BoothFixture boothFixture;

    @Autowired
    private QueueCachePort queueCachePort;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private Long testUniversityId;
    private Long testUserId;

    @BeforeEach
    void setUp() {
        // Redis 초기화
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();

        // 대학교 생성
        String uniqueDomain = "TEST-UNIV-" + System.currentTimeMillis();
        UniversityEntity university = new UniversityEntity("테스트대학교", uniqueDomain);
        testUniversityId = universityRepository.save(university).getId();

        // 사용자 생성
        testUserId = userFixture.createVisitor("테스트유저");
    }

    @Test
    @DisplayName("동시에 3개 부스 등록 시도 → 2개만 성공, 1개 실패 (Race Condition 방지)")
    void concurrentEnqueueShouldPreventRaceCondition() throws InterruptedException {
        // Given: 3개 부스 생성
        UniversityEntity university = universityRepository.findById(testUniversityId).orElseThrow();
        Long boothId1 = boothFixture.createOpenBooth(university, "부스1", 10);
        Long boothId2 = boothFixture.createOpenBooth(university, "부스2", 10);
        Long boothId3 = boothFixture.createOpenBooth(university, "부스3", 10);

        List<Long> boothIds = List.of(boothId1, boothId2, boothId3);

        // 동시 실행을 위한 준비
        int threadCount = 3;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Exception> exceptions = new CopyOnWriteArrayList<>();

        // When: 3개 스레드가 동시에 enqueue 시도
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await(); // 모든 스레드가 준비될 때까지 대기

                    // 동시 실행
                    EnqueueCommand command = new EnqueueCommand(testUserId, boothIds.get(index));
                    enqueueUseCase.enqueue(command);
                    successCount.incrementAndGet();

                } catch (MaxWaitingExceededException e) {
                    // 최대 부스 수 초과 (정상)
                    failureCount.incrementAndGet();
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // 모든 스레드가 준비될 때까지 대기
        readyLatch.await();

        // 동시 시작!
        startLatch.countDown();

        // 모든 스레드 완료 대기 (최대 5초)
        boolean finished = doneLatch.await(5, TimeUnit.SECONDS);
        executorService.shutdown();

        // Then: 검증
        assertThat(finished).isTrue(); // 타임아웃 없이 완료
        assertThat(exceptions).isEmpty(); // 예상치 못한 예외 없음
        assertThat(successCount.get()).isEqualTo(2); // 2개만 성공
        assertThat(failureCount.get()).isEqualTo(1); // 1개는 MAX_BOOTHS_EXCEEDED

        // Redis 상태 검증
        int activeBoothCount = queueCachePort.getUserActiveBoothCount(testUserId);
        assertThat(activeBoothCount).isEqualTo(2); // 정확히 2개만 등록됨
    }

    @Test
    @DisplayName("동일 부스에 동시 등록 → 1개만 성공, 나머지는 멱등성으로 같은 결과 반환")
    void concurrentEnqueueToSameBoothShouldBeIdempotent() throws InterruptedException {
        // Given: 1개 부스 생성
        UniversityEntity university = universityRepository.findById(testUniversityId).orElseThrow();
        Long boothId = boothFixture.createOpenBooth(university, "부스1", 10);

        // 동시 실행 준비
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        List<Integer> positions = new CopyOnWriteArrayList<>();
        List<Exception> exceptions = new CopyOnWriteArrayList<>();

        // When: 10개 스레드가 동시에 동일 부스 등록 시도
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();

                    // 동시 실행
                    EnqueueCommand command = new EnqueueCommand(testUserId, boothId);
                    var result = enqueueUseCase.enqueue(command);
                    positions.add(result.position());

                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        boolean finished = doneLatch.await(5, TimeUnit.SECONDS);
        executorService.shutdown();

        // Then: 검증
        assertThat(finished).isTrue();
        assertThat(exceptions).isEmpty();
        assertThat(positions).hasSize(threadCount); // 모든 요청 성공 (멱등성)
        assertThat(positions).containsOnly(1); // 모두 동일한 position 반환 (1번)

        // Redis 상태 검증
        int activeBoothCount = queueCachePort.getUserActiveBoothCount(testUserId);
        assertThat(activeBoothCount).isEqualTo(1); // 정확히 1개만 등록됨

        int queueSize = queueCachePort.getQueueSize(boothId);
        assertThat(queueSize).isEqualTo(1); // 대기열에도 1명만 존재
    }

    @Test
    @DisplayName("100번 동시 실행 → 항상 2개만 성공 (안정성 검증)")
    void stressTestWith100Iterations() throws InterruptedException {
        // 100번 반복해도 Race Condition이 발생하지 않는지 검증
        for (int iteration = 0; iteration < 100; iteration++) {
            // Redis 초기화
            redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();

            // 부스 3개 생성
            UniversityEntity university = universityRepository.findById(testUniversityId).orElseThrow();
            List<Long> boothIds = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                boothIds.add(boothFixture.createOpenBooth(university, "부스" + i, 10));
            }

            // 동시 실행
            ExecutorService executor = Executors.newFixedThreadPool(3);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(3);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < 3; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        EnqueueCommand command = new EnqueueCommand(testUserId, boothIds.get(index));
                        enqueueUseCase.enqueue(command);
                        successCount.incrementAndGet();
                    } catch (MaxWaitingExceededException e) {
                        // Expected
                    } catch (Exception e) {
                        // Unexpected
                        throw new RuntimeException(e);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await(2, TimeUnit.SECONDS);
            executor.shutdown();

            // 검증: 항상 2개만 성공해야 함
            int activeBoothCount = queueCachePort.getUserActiveBoothCount(testUserId);
            assertThat(activeBoothCount)
                    .withFailMessage("Iteration %d: activeBoothCount should be 2, but was %d",
                            iteration, activeBoothCount)
                    .isEqualTo(2);
        }
    }
}