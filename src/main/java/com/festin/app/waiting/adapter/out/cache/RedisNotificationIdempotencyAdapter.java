package com.festin.app.waiting.adapter.out.cache;

import com.festin.app.waiting.application.port.out.NotificationIdempotencyPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis 기반 알림 중복 방지 Adapter
 *
 * Redis의 SETNX 연산을 사용하여 동일한 알림이 중복 발송되지 않도록 합니다.
 */
@Component
@RequiredArgsConstructor
public class RedisNotificationIdempotencyAdapter implements NotificationIdempotencyPort {

    private static final String KEY_PREFIX = "notification:processed:";
    private static final Duration TTL = Duration.ofHours(24); // 24시간 후 자동 삭제

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean tryProcess(String eventId) {
        String key = KEY_PREFIX + eventId;
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(key, "PROCESSING", TTL);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void markProcessed(String eventId) {
        String key = KEY_PREFIX + eventId;
        redisTemplate.opsForValue().set(key, "COMPLETED", TTL);
    }
}
