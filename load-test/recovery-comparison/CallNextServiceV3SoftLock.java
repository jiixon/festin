package com.festin.app.waiting.application.service;

import com.festin.app.booth.application.port.out.BoothCachePort;
import com.festin.app.booth.domain.BoothNotFoundException;
import com.festin.app.booth.domain.model.Booth;
import com.festin.app.booth.domain.model.BoothStatus;
import com.festin.app.waiting.application.port.in.CallNextUseCase;
import com.festin.app.waiting.application.port.in.result.CallResult;
import com.festin.app.waiting.application.port.out.NotificationPort;
import com.festin.app.waiting.application.port.out.QueueCachePort;
import com.festin.app.waiting.application.port.out.WaitingRepositoryPort;
import com.festin.app.waiting.domain.exception.QueueEmptyException;
import com.festin.app.waiting.domain.model.Waiting;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Soft Lock 방식 CallNext Service (실험용)
 *
 * 흐름:
 * 1. 분산 락 획득
 * 2. Soft Lock 생성 (temp:calling:{boothId}:{userId})
 * 3. Redis dequeue + removeUserActiveBooth
 * 4. MySQL save (@Transactional)
 * 5. 성공 시: Soft Lock 삭제
 * 6. 실패 시: Soft Lock 남김 (배치가 롤백)
 * 7. 분산 락 해제
 *
 * 장점:
 * - 자연스러운 흐름 (Redis → MySQL)
 * - Soft Lock으로 실패 지점 마킹
 * - 배치가 Soft Lock만 스캔 (O(실패 건수))
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class  CallNextServiceV3SoftLock implements CallNextUseCase {

    private final BoothCachePort boothCachePort;
    private final QueueCachePort queueCachePort;
    private final WaitingRepositoryPort waitingRepositoryPort;
    private final NotificationPort notificationPort;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String SOFT_LOCK_PREFIX = "temp:calling:";

    @Override
    @Transactional
    public CallResult callNext(Long boothId) {
        // Redis에서 부스 실시간 정보 조회
        BoothStatus status = boothCachePort.getStatus(boothId)
                .orElseThrow(BoothNotFoundException::new);
        Integer capacity = boothCachePort.getCapacity(boothId)
                .orElseThrow(BoothNotFoundException::new);
        String boothName = boothCachePort.getName(boothId)
                .orElseThrow(BoothNotFoundException::new);

        // Booth 도메인 객체 생성
        Booth booth = Booth.of(boothId, boothName, capacity, status);

        // 호출 가능 여부 검증
        int currentCount = boothCachePort.getCurrentCount(boothId);
        booth.validateForCalling(currentCount);

        // Redis dequeue: 즉시 제거 (원자적)
        QueueCachePort.QueueItem queueItem = queueCachePort.dequeue(boothId)
                .orElseThrow(QueueEmptyException::new);

        Long userId = queueItem.userId();
        LocalDateTime registeredAt = queueItem.registeredAt();

        // ✅ Soft Lock 생성 (복구 지점 마킹)
        String softLockKey = SOFT_LOCK_PREFIX + boothId + ":" + userId;
        createSoftLock(softLockKey, boothId, userId, registeredAt);

        log.info("[Soft Lock] 생성 완료 - key: {}, userId: {}, boothId: {}", softLockKey, userId, boothId);

        try {
            // 활성 부스 목록에서 제거
            queueCachePort.removeUserActiveBooth(userId, boothId);

            // Waiting 도메인 생성 (CALLED 상태)
            LocalDateTime calledAt = LocalDateTime.now();
            Waiting waiting = Waiting.ofCalled(
                    userId,
                    boothId,
                    1,  // 호출 순번
                    registeredAt,
                    calledAt
            );

            // MySQL에 저장 (트랜잭션 범위)
            Waiting savedWaiting = waitingRepositoryPort.save(waiting);

            log.info("[Soft Lock] MySQL 저장 성공 - waitingId: {}, userId: {}, boothId: {}",
                    savedWaiting.getId(), userId, boothId);

            // ✅ 성공: Soft Lock 삭제
            deleteSoftLock(softLockKey);

            log.info("[Soft Lock] 삭제 완료 (성공) - key: {}", softLockKey);

            // 푸시 알림 발송
            sendNotification(boothId, userId, boothName);

            // 결과 반환
            return new CallResult(
                    savedWaiting.getId(),
                    userId,
                    1,
                    calledAt
            );

        } catch (Exception e) {
            // ⚠️ MySQL 실패: Soft Lock 남김
            log.error("[Soft Lock] MySQL 저장 실패 - Soft Lock 남김 (배치가 롤백) - key: {}, userId: {}, boothId: {}",
                    softLockKey, userId, boothId, e);
            // Soft Lock은 삭제하지 않음 → 배치가 감지하여 롤백
            throw e;
        }
    }

    /**
     * Soft Lock 생성
     */
    private void createSoftLock(String key, Long boothId, Long userId, LocalDateTime registeredAt) {
        Map<String, String> data = new HashMap<>();
        data.put("boothId", boothId.toString());
        data.put("userId", userId.toString());
        data.put("timestamp", String.valueOf(registeredAt.toEpochSecond(ZoneOffset.UTC)));
        data.put("createdAt", String.valueOf(System.currentTimeMillis()));

        redisTemplate.opsForHash().putAll(key, data);
        // TTL 없음 (영구 보존, 배치가 삭제)
    }

    /**
     * Soft Lock 삭제
     */
    private void deleteSoftLock(String key) {
        redisTemplate.delete(key);
    }

    private void sendNotification(Long boothId, Long userId, String boothName) {
        try {
            String eventId = "call:" + boothId + ":" + userId;
            NotificationPort.CallNotification notification = new NotificationPort.CallNotification(
                    eventId,
                    userId,
                    boothId,
                    boothName,
                    1
            );
            notificationPort.send(notification);
        } catch (Exception e) {
            log.error("[Soft Lock] 푸시 알림 실패 - boothId: {}, userId: {}", boothId, userId, e);
        }
    }
}