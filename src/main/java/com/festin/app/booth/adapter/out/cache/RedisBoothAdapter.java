package com.festin.app.booth.adapter.out.cache;

import com.festin.app.booth.application.port.out.BoothCachePort;
import com.festin.app.booth.domain.model.BoothStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Redis Booth Adapter
 *
 * BoothCachePort 구현체
 * Redis를 사용한 부스 실시간 상태 관리
 *
 * Redis 키 구조:
 * - booth:{boothId}:current (String) - 현재 인원 (원자적 증감용)
 * - booth:{boothId}:meta (HASH) - 부스 메타 정보
 *   - name: 부스 이름
 *   - capacity: 최대 정원
 *   - status: 운영 상태 (OPEN/CLOSED)
 */
@Component
@RequiredArgsConstructor
public class RedisBoothAdapter implements BoothCachePort {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String BOOTH_KEY_PREFIX = "booth:";
    private static final String CURRENT_KEY_SUFFIX = ":current";
    private static final String META_KEY_SUFFIX = ":meta";

    // HASH fields
    private static final String FIELD_NAME = "name";
    private static final String FIELD_CAPACITY = "capacity";
    private static final String FIELD_STATUS = "status";

    @Override
    public int incrementCurrentCount(Long boothId) {
        String key = BOOTH_KEY_PREFIX + boothId + CURRENT_KEY_SUFFIX;
        Long count = redisTemplate.opsForValue().increment(key);
        return count != null ? count.intValue() : 1;
    }

    @Override
    public int decrementCurrentCount(Long boothId) {
        String key = BOOTH_KEY_PREFIX + boothId + CURRENT_KEY_SUFFIX;
        Long count = redisTemplate.opsForValue().decrement(key);
        return count != null ? count.intValue() : 0;
    }

    @Override
    public int getCurrentCount(Long boothId) {
        String key = BOOTH_KEY_PREFIX + boothId + CURRENT_KEY_SUFFIX;
        String value = redisTemplate.opsForValue().get(key);
        return value != null ? Integer.parseInt(value) : 0;
    }

    @Override
    public void setStatus(Long boothId, BoothStatus status) {
        String key = BOOTH_KEY_PREFIX + boothId + META_KEY_SUFFIX;
        redisTemplate.opsForHash().put(key, FIELD_STATUS, status.name());
    }

    @Override
    public Optional<BoothStatus> getStatus(Long boothId) {
        String key = BOOTH_KEY_PREFIX + boothId + META_KEY_SUFFIX;
        Object value = redisTemplate.opsForHash().get(key, FIELD_STATUS);

        if (value == null) {
            return Optional.empty();
        }

        return Optional.of(BoothStatus.valueOf(value.toString()));
    }

    @Override
    public void setCapacity(Long boothId, int capacity) {
        String key = BOOTH_KEY_PREFIX + boothId + META_KEY_SUFFIX;
        redisTemplate.opsForHash().put(key, FIELD_CAPACITY, String.valueOf(capacity));
    }

    @Override
    public Optional<Integer> getCapacity(Long boothId) {
        String key = BOOTH_KEY_PREFIX + boothId + META_KEY_SUFFIX;
        Object value = redisTemplate.opsForHash().get(key, FIELD_CAPACITY);

        if (value == null) {
            return Optional.empty();
        }

        return Optional.of(Integer.parseInt(value.toString()));
    }

    @Override
    public void setName(Long boothId, String name) {
        String key = BOOTH_KEY_PREFIX + boothId + META_KEY_SUFFIX;
        redisTemplate.opsForHash().put(key, FIELD_NAME, name);
    }

    @Override
    public Optional<String> getName(Long boothId) {
        String key = BOOTH_KEY_PREFIX + boothId + META_KEY_SUFFIX;
        Object value = redisTemplate.opsForHash().get(key, FIELD_NAME);

        if (value == null) {
            return Optional.empty();
        }

        return Optional.of(value.toString());
    }
}