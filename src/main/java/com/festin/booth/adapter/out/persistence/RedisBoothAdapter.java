package com.festin.booth.adapter.out.cache;

import com.festin.booth.application.port.out.BoothCachePort;
import com.festin.booth.domain.model.BoothStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Redis Booth Adapter
 *
 * BoothCachePort 구현체
 * Redis를 사용한 부스 실시간 상태 관리
 */
@Component
@RequiredArgsConstructor
public class RedisBoothAdapter implements BoothCachePort {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String BOOTH_CURRENT_KEY_PREFIX = "booth:";
    private static final String BOOTH_CURRENT_KEY_SUFFIX = ":current";
    private static final String BOOTH_STATUS_KEY_SUFFIX = ":status";
    private static final String BOOTH_CAPACITY_KEY_SUFFIX = ":capacity";

    @Override
    public int incrementCurrentCount(Long boothId) {
        String key = BOOTH_CURRENT_KEY_PREFIX + boothId + BOOTH_CURRENT_KEY_SUFFIX;
        Long count = redisTemplate.opsForValue().increment(key);
        return count != null ? count.intValue() : 1;
    }

    @Override
    public int decrementCurrentCount(Long boothId) {
        String key = BOOTH_CURRENT_KEY_PREFIX + boothId + BOOTH_CURRENT_KEY_SUFFIX;
        Long count = redisTemplate.opsForValue().decrement(key);
        return count != null ? count.intValue() : 0;
    }

    @Override
    public int getCurrentCount(Long boothId) {
        String key = BOOTH_CURRENT_KEY_PREFIX + boothId + BOOTH_CURRENT_KEY_SUFFIX;
        String value = redisTemplate.opsForValue().get(key);
        return value != null ? Integer.parseInt(value) : 0;
    }

    @Override
    public void setStatus(Long boothId, BoothStatus status) {
        String key = BOOTH_CURRENT_KEY_PREFIX + boothId + BOOTH_STATUS_KEY_SUFFIX;
        redisTemplate.opsForValue().set(key, status.name());
    }

    @Override
    public Optional<BoothStatus> getStatus(Long boothId) {
        String key = BOOTH_CURRENT_KEY_PREFIX + boothId + BOOTH_STATUS_KEY_SUFFIX;
        String value = redisTemplate.opsForValue().get(key);

        if (value == null) {
            return Optional.empty();
        }

        return Optional.of(BoothStatus.valueOf(value));
    }

    @Override
    public void setCapacity(Long boothId, int capacity) {
        String key = BOOTH_CURRENT_KEY_PREFIX + boothId + BOOTH_CAPACITY_KEY_SUFFIX;
        redisTemplate.opsForValue().set(key, String.valueOf(capacity));
    }

    @Override
    public Optional<Integer> getCapacity(Long boothId) {
        String key = BOOTH_CURRENT_KEY_PREFIX + boothId + BOOTH_CAPACITY_KEY_SUFFIX;
        String value = redisTemplate.opsForValue().get(key);

        if (value == null) {
            return Optional.empty();
        }

        return Optional.of(Integer.parseInt(value));
    }
}