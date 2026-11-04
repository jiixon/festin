package com.festin.app.waiting.adapter.scheduler;

import com.festin.app.waiting.application.port.out.QueueCachePort;
import com.festin.app.waiting.application.port.out.WaitingRepositoryPort;
import com.festin.app.waiting.domain.model.Waiting;
import com.festin.app.waiting.domain.model.WaitingStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * TransactionalEventListener 방식의 배치 보정
 *
 * 문제:
 * - AFTER_COMMIT에서 Redis dequeue 실패 시
 * - MySQL에는 CALLED 상태로 저장됨
 * - Redis에는 여전히 대기열에 존재
 *
 * 해결:
 * - 최근 5분 CALLED 상태 조회 (MySQL Full Scan)
 * - 각 userId가 Redis에도 있는지 확인
 * - 있으면 Redis에서 제거
 *
 * ⚠️ 비효율:
 * - MySQL에서 CALLED 상태 전체 조회 (Full Scan)
 * - 각 항목마다 Redis 조회 필요
 * - O(N) where N = 최근 CALLED 수
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventListenerRecoveryBatch {

    private final WaitingRepositoryPort waitingRepositoryPort;
    private final QueueCachePort queueCachePort;
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 1분마다 실행
     */
    @Scheduled(fixedRate = 60_000)
    public void recoverAfterCommitFailures() {
        long startTime = System.currentTimeMillis();
        log.info("[EventListener 배치] 보정 시작");

        try {
            // 최근 5분 CALLED 상태 조회 (MySQL)
            LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);
            List<Waiting> recentCalled = waitingRepositoryPort.findRecentByStatus(
                    WaitingStatus.CALLED,
                    fiveMinutesAgo
            );

            int recoveredCount = 0;

            // 각 CALLED 건마다 Redis 확인
            for (Waiting waiting : recentCalled) {
                Long boothId = waiting.getBoothId();
                Long userId = waiting.getUserId();

                // Redis에 여전히 존재하는지 확인
                if (isStillInQueue(boothId, userId)) {
                    // Redis에서 제거 (복구)
                    queueCachePort.remove(boothId, userId);
                    queueCachePort.removeUserActiveBooth(userId, boothId);

                    log.warn("[EventListener 배치] 복구 완료 - waitingId: {}, userId: {}, boothId: {}",
                            waiting.getId(), userId, boothId);
                    recoveredCount++;
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("[EventListener 배치] 보정 완료 - 조회: {}, 복구: {}, 소요: {}ms",
                    recentCalled.size(), recoveredCount, duration);

        } catch (Exception e) {
            log.error("[EventListener 배치] 보정 실패", e);
        }
    }

    /**
     * Redis 대기열에 여전히 존재하는지 확인
     */
    private boolean isStillInQueue(Long boothId, Long userId) {
        String queueKey = "queue:booth:" + boothId;
        Double score = redisTemplate.opsForZSet().score(queueKey, userId.toString());
        return score != null;
    }
}