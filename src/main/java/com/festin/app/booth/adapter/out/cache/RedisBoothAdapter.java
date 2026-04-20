package com.festin.app.booth.adapter.out.cache;

import com.festin.app.booth.application.port.out.BoothCachePort;
import com.festin.app.booth.domain.model.Booth;
import com.festin.app.booth.domain.model.BoothStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
    private static final String FIELD_DESCRIPTION = "description";
    private static final String FIELD_UNIVERSITY_NAME = "universityName";

    // 부스 ID 목록 관리용 키
    private static final String BOOTH_IDS_KEY = "booth:ids";

    @Override
    public Optional<Booth> getBooth(Long boothId) {
        Optional<BoothStatus> status = getStatus(boothId);
        Optional<Integer> capacity = getCapacity(boothId);
        Optional<String> name = getName(boothId);

        if (status.isEmpty() || capacity.isEmpty() || name.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(Booth.of(boothId, name.get(), capacity.get(), status.get()));
    }

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

    @Override
    public void setDescription(Long boothId, String description) {
        String key = BOOTH_KEY_PREFIX + boothId + META_KEY_SUFFIX;
        redisTemplate.opsForHash().put(key, FIELD_DESCRIPTION, description);
    }

    @Override
    public Optional<String> getDescription(Long boothId) {
        String key = BOOTH_KEY_PREFIX + boothId + META_KEY_SUFFIX;
        Object value = redisTemplate.opsForHash().get(key, FIELD_DESCRIPTION);

        if (value == null) {
            return Optional.empty();
        }

        return Optional.of(value.toString());
    }

    @Override
    public void setUniversityName(Long boothId, String universityName) {
        String key = BOOTH_KEY_PREFIX + boothId + META_KEY_SUFFIX;
        redisTemplate.opsForHash().put(key, FIELD_UNIVERSITY_NAME, universityName);
    }

    @Override
    public Optional<String> getUniversityName(Long boothId) {
        String key = BOOTH_KEY_PREFIX + boothId + META_KEY_SUFFIX;
        Object value = redisTemplate.opsForHash().get(key, FIELD_UNIVERSITY_NAME);

        if (value == null) {
            return Optional.empty();
        }

        return Optional.of(value.toString());
    }

    @Override
    public Map<Long, BoothMeta> getBoothMetas(List<Long> boothIds) {
        if (boothIds == null || boothIds.isEmpty()) {
            return Map.of();
        }

        // Pipeline으로 모든 부스의 메타 정보 한 번에 조회
        List<Object> results = redisTemplate.executePipelined(
                (org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                    for (Long boothId : boothIds) {
                        byte[] key = (BOOTH_KEY_PREFIX + boothId + META_KEY_SUFFIX).getBytes();
                        connection.hGetAll(key);
                    }
                    return null;
                });

        // 결과 매핑
        Map<Long, BoothMeta> metaMap = new HashMap<>();
        for (int i = 0; i < boothIds.size(); i++) {
            Long boothId = boothIds.get(i);
            @SuppressWarnings("unchecked")
            Map<Object, Object> data = (Map<Object, Object>) results.get(i);

            if (data != null && !data.isEmpty()) {
                String name = getStringValue(data, FIELD_NAME);
                String description = getStringValue(data, FIELD_DESCRIPTION);
                String universityName = getStringValue(data, FIELD_UNIVERSITY_NAME);
                String statusStr = getStringValue(data, FIELD_STATUS);
                String capacityStr = getStringValue(data, FIELD_CAPACITY);

                BoothStatus status = statusStr != null ? BoothStatus.valueOf(statusStr) : null;
                int capacity = capacityStr != null ? Integer.parseInt(capacityStr) : 0;

                metaMap.put(boothId, new BoothMeta(
                        boothId, name, description, universityName, status, capacity
                ));
            }
        }
        return metaMap;
    }

    private String getStringValue(Map<Object, Object> data, String field) {
        Object value = data.get(field);
        return value != null ? value.toString() : null;
    }

    @Override
    public List<Long> getAllBoothIds() {
        Set<String> ids = redisTemplate.opsForSet().members(BOOTH_IDS_KEY);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .map(Long::parseLong)
                .sorted()
                .toList();
    }

    @Override
    public void addBoothId(Long boothId) {
        redisTemplate.opsForSet().add(BOOTH_IDS_KEY, boothId.toString());
    }
}