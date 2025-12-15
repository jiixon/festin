package com.festin.waiting.adapter.out.cache;

import com.festin.waiting.application.port.out.IdempotencyCachePort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis Idempotency Adapter
 *
 * IdempotencyCachePort 구현체
 * Redis를 사용한 멱등성 키 관리
 */
@Component
@RequiredArgsConstructor
public class RedisIdempotencyAdapter implements IdempotencyCachePort {

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public boolean exists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    @Override
    public void save(String key, long ttlSeconds) {
        redisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(ttlSeconds));
    }

    @Override
    public void delete(String key) {
        redisTemplate.delete(key);
    }
}