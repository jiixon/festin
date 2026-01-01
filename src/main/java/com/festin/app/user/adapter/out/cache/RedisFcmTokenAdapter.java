package com.festin.app.user.adapter.out.cache;

import com.festin.app.user.application.port.out.FcmTokenCachePort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * FCM 토큰 Redis Adapter
 *
 * Redis에 FCM 토큰을 캐싱합니다.
 * - 키: fcm:token:{userId}
 * - TTL: 60일 (5184000초)
 */
@Component
@RequiredArgsConstructor
public class RedisFcmTokenAdapter implements FcmTokenCachePort {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String FCM_TOKEN_KEY_PREFIX = "fcm:token:";
    private static final Duration FCM_TOKEN_TTL = Duration.ofDays(60);

    @Override
    public void saveFcmToken(Long userId, String fcmToken) {
        String key = FCM_TOKEN_KEY_PREFIX + userId;
        redisTemplate.opsForValue().set(key, fcmToken, FCM_TOKEN_TTL);
    }

    @Override
    public Optional<String> getFcmToken(Long userId) {
        String key = FCM_TOKEN_KEY_PREFIX + userId;
        String token = redisTemplate.opsForValue().get(key);
        return Optional.ofNullable(token);
    }

    @Override
    public void deleteFcmToken(Long userId) {
        String key = FCM_TOKEN_KEY_PREFIX + userId;
        redisTemplate.delete(key);
    }
}