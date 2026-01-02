package com.festin.app.waiting.adapter.out.cache;

import com.festin.app.waiting.application.port.out.QueueCachePort;
import com.festin.app.waiting.domain.exception.QueueOperationException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Redis Queue Adapter
 *
 * QueueCachePort 구현체
 * Redis Sorted Set을 사용한 대기열 관리
 * Redis Set을 사용한 사용자별 활성 부스 목록 관리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisQueueAdapter implements QueueCachePort {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String QUEUE_KEY_PREFIX = "queue:booth:";
    private static final String USER_ACTIVE_BOOTHS_KEY_PREFIX = "user:";
    private static final String USER_ACTIVE_BOOTHS_KEY_SUFFIX = ":active_booths";

    /**
     * 원자적 enqueue Lua Script
     */
    private RedisScript<List> enqueueAtomicScript;

    /**
     * Lua Script 초기화
     */
    @PostConstruct
    public void init() {
        try {
            ClassPathResource resource = new ClassPathResource("scripts/enqueue-atomic.lua");
            String scriptContent = resource.getContentAsString(StandardCharsets.UTF_8);

            DefaultRedisScript<List> script = new DefaultRedisScript<>();
            script.setScriptText(scriptContent);
            script.setResultType(List.class);

            this.enqueueAtomicScript = script;
            log.info("Lua script 로드 완료: enqueue-atomic.lua");
        } catch (IOException e) {
            log.error("Lua script 로드 실패", e);
            throw new RuntimeException("Lua script 로드 실패", e);
        }
    }

    @Override
    public boolean enqueue(Long boothId, Long userId, LocalDateTime registeredAt) {
        String key = QUEUE_KEY_PREFIX + boothId;
        double score = registeredAt.toEpochSecond(ZoneOffset.UTC);

        Boolean added = redisTemplate.opsForZSet().add(key, userId.toString(), score);
        return Boolean.TRUE.equals(added);
    }

    @Override
    public Optional<QueueItem> dequeue(Long boothId) {
        String key = QUEUE_KEY_PREFIX + boothId;

        // ZPOPMIN: 가장 낮은 score (가장 먼저 등록한 사람)를 원자적으로 가져오고 제거
        ZSetOperations.TypedTuple<String> tuple = redisTemplate.opsForZSet()
            .popMin(key);

        if (tuple == null || tuple.getValue() == null || tuple.getScore() == null) {
            return Optional.empty();
        }

        Long userId = Long.parseLong(tuple.getValue());
        LocalDateTime registeredAt = LocalDateTime.ofEpochSecond(
                tuple.getScore().longValue(),
                0,
                ZoneOffset.UTC
        );

        return Optional.of(new QueueItem(userId, registeredAt));
    }

    @Override
    public Optional<Integer> getPosition(Long boothId, Long userId) {
        String key = QUEUE_KEY_PREFIX + boothId;

        // rank는 0부터 시작하므로 +1
        Long rank = redisTemplate.opsForZSet().rank(key, userId.toString());

        if (rank == null) {
            return Optional.empty();
        }

        return Optional.of(rank.intValue() + 1);
    }

    @Override
    public int getQueueSize(Long boothId) {
        String key = QUEUE_KEY_PREFIX + boothId;

        Long size = redisTemplate.opsForZSet().size(key);
        return size != null ? size.intValue() : 0;
    }

    @Override
    public boolean remove(Long boothId, Long userId) {
        String key = QUEUE_KEY_PREFIX + boothId;

        Long removed = redisTemplate.opsForZSet().remove(key, userId.toString());
        return removed != null && removed > 0;
    }

    @Override
    public Optional<LocalDateTime> getRegisteredAt(Long boothId, Long userId) {
        String key = QUEUE_KEY_PREFIX + boothId;

        // score(등록 시간)를 조회
        Double score = redisTemplate.opsForZSet().score(key, userId.toString());

        if (score == null) {
            return Optional.empty();
        }

        // Epoch seconds → LocalDateTime 변환
        return Optional.of(LocalDateTime.ofEpochSecond(score.longValue(), 0, ZoneOffset.UTC));
    }

    @Override
    public int getUserActiveBoothCount(Long userId) {
        String key = USER_ACTIVE_BOOTHS_KEY_PREFIX + userId + USER_ACTIVE_BOOTHS_KEY_SUFFIX;

        Long count = redisTemplate.opsForSet().size(key);
        return count != null ? count.intValue() : 0;
    }

    @Override
    public void addUserActiveBooth(Long userId, Long boothId) {
        String key = USER_ACTIVE_BOOTHS_KEY_PREFIX + userId + USER_ACTIVE_BOOTHS_KEY_SUFFIX;

        redisTemplate.opsForSet().add(key, boothId.toString());
    }

    @Override
    public void removeUserActiveBooth(Long userId, Long boothId) {
        String key = USER_ACTIVE_BOOTHS_KEY_PREFIX + userId + USER_ACTIVE_BOOTHS_KEY_SUFFIX;

        redisTemplate.opsForSet().remove(key, boothId.toString());
    }

    @Override
    public Set<Long> getUserActiveBooths(Long userId) {
        String key = USER_ACTIVE_BOOTHS_KEY_PREFIX + userId + USER_ACTIVE_BOOTHS_KEY_SUFFIX;

        Set<String> boothIdStrings = redisTemplate.opsForSet().members(key);

        if (boothIdStrings == null || boothIdStrings.isEmpty()) {
            return Set.of();
        }

        return boothIdStrings.stream()
                .map(Long::parseLong)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Override
    public EnqueueAtomicResult enqueueAtomic(Long boothId, Long userId, LocalDateTime registeredAt, int maxActiveBooths) {
        // Redis 키 준비
        String queueKey = QUEUE_KEY_PREFIX + boothId;
        String activeBoothsKey = USER_ACTIVE_BOOTHS_KEY_PREFIX + userId + USER_ACTIVE_BOOTHS_KEY_SUFFIX;

        // KEYS 배열
        List<String> keys = List.of(queueKey, activeBoothsKey);

        // ARGV 배열
        long timestamp = registeredAt.toEpochSecond(ZoneOffset.UTC);
        Object[] args = {
                userId.toString(),
                boothId.toString(),
                String.valueOf(timestamp),
                String.valueOf(maxActiveBooths)
        };

        // Lua Script 실행
        List result = redisTemplate.execute(enqueueAtomicScript, keys, args);

        // 결과 검증
        if (result == null || result.size() != 3) {
            log.error("Lua script 반환값 이상 - result: {}", result);
            throw QueueOperationException.scriptExecutionFailed();
        }

        // 결과 파싱
        int statusCode = ((Number) result.get(0)).intValue();
        int position = ((Number) result.get(1)).intValue();
        int totalWaiting = ((Number) result.get(2)).intValue();

        // 상태 코드 변환
        EnqueueStatus status = switch (statusCode) {
            case 1 -> EnqueueStatus.SUCCESS;
            case 2 -> EnqueueStatus.ALREADY_ENQUEUED;
            case 0 -> EnqueueStatus.MAX_BOOTHS_EXCEEDED;
            default -> {
                log.error("알 수 없는 상태 코드: {}", statusCode);
                throw QueueOperationException.unknownScriptResult(statusCode);
            }
        };

        log.debug("원자적 등록 결과 - userId: {}, boothId: {}, status: {}, position: {}, totalWaiting: {}",
                userId, boothId, status, position, totalWaiting);

        return new EnqueueAtomicResult(status, position, totalWaiting);
    }
}