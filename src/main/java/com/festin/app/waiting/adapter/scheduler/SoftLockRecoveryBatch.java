package com.festin.app.waiting.adapter.scheduler;

import com.festin.app.waiting.application.port.out.QueueCachePort;
import com.festin.app.waiting.application.port.out.WaitingRepositoryPort;
import com.festin.app.waiting.domain.model.WaitingStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

/**
 * Soft Lock 방식의 배치 보정
 *
 * 장점:
 * - Soft Lock만 스캔 (O(실패 건수))
 * - MySQL 조회 최소화
 * - 원래 timestamp 보존 (정확한 순서 복구)
 *
 * 흐름:
 * 1. Redis SCAN temp:calling:*
 * 2. 각 Soft Lock에 대해:
 *    - MySQL 확인 (CALLED 상태 존재?)
 *    - 존재: Soft Lock만 삭제 (정상 완료)
 *    - 없음: Redis 롤백 (ZADD + SADD) + Soft Lock 삭제
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SoftLockRecoveryBatch {

    private final WaitingRepositoryPort waitingRepositoryPort;
    private final QueueCachePort queueCachePort;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String SOFT_LOCK_PATTERN = "temp:calling:*";
    private static final String QUEUE_KEY_PREFIX = "queue:booth:";
    private static final String USER_ACTIVE_BOOTHS_KEY_PREFIX = "user:";
    private static final String USER_ACTIVE_BOOTHS_KEY_SUFFIX = ":active_booths";

    /**
     * 1분마다 실행
     */
    @Scheduled(fixedRate = 60_000)
    public void recoverSoftLockFailures() {
        long startTime = System.currentTimeMillis();
        log.info("[Soft Lock 배치] 보정 시작");

        try {
            int scannedCount = 0;
            int recoveredCount = 0;

            // Soft Lock 스캔
            ScanOptions options = ScanOptions.scanOptions()
                    .match(SOFT_LOCK_PATTERN)
                    .count(100)
                    .build();

            Cursor<byte[]> cursor = redisTemplate.getConnectionFactory()
                    .getConnection()
                    .scan(options);

            while (cursor.hasNext()) {
                String softLockKey = new String(cursor.next());
                scannedCount++;

                // Soft Lock 데이터 조회
                Map<Object, Object> data = redisTemplate.opsForHash().entries(softLockKey);
                if (data.isEmpty()) {
                    continue;
                }

                Long boothId = Long.parseLong((String) data.get("boothId"));
                Long userId = Long.parseLong((String) data.get("userId"));
                long timestamp = Long.parseLong((String) data.get("timestamp"));

                // MySQL 확인: CALLED 상태 존재?
                boolean savedInMySQL = waitingRepositoryPort.existsByUserIdAndBoothIdAndStatus(
                        userId,
                        boothId,
                        WaitingStatus.CALLED
                );

                if (savedInMySQL) {
                    // 정상 완료: Soft Lock만 삭제
                    redisTemplate.delete(softLockKey);
                    log.debug("[Soft Lock 배치] 정상 완료 확인 - userId: {}, boothId: {}", userId, boothId);

                } else {
                    // MySQL 실패: Redis 롤백
                    rollbackRedis(boothId, userId, timestamp);
                    redisTemplate.delete(softLockKey);

                    log.warn("[Soft Lock 배치] 복구 완료 (롤백) - userId: {}, boothId: {}, timestamp: {}",
                            userId, boothId, timestamp);
                    recoveredCount++;
                }
            }

            cursor.close();

            long duration = System.currentTimeMillis() - startTime;
            log.info("[Soft Lock 배치] 보정 완료 - 스캔: {}, 복구: {}, 소요: {}ms",
                    scannedCount, recoveredCount, duration);

        } catch (Exception e) {
            log.error("[Soft Lock 배치] 보정 실패", e);
        }
    }

    /**
     * Redis 롤백: 대기열 복원 + 활성 부스 복원
     */
    private void rollbackRedis(Long boothId, Long userId, long timestamp) {
        // 대기열 복원 (원래 timestamp로 정확한 위치에 복구)
        String queueKey = QUEUE_KEY_PREFIX + boothId;
        redisTemplate.opsForZSet().add(queueKey, userId.toString(), timestamp);

        // 활성 부스 복원
        String activeBoothsKey = USER_ACTIVE_BOOTHS_KEY_PREFIX + userId + USER_ACTIVE_BOOTHS_KEY_SUFFIX;
        redisTemplate.opsForSet().add(activeBoothsKey, boothId.toString());

        log.debug("[Soft Lock 배치] Redis 롤백 완료 - userId: {}, boothId: {}, timestamp: {}",
                userId, boothId, timestamp);
    }
}