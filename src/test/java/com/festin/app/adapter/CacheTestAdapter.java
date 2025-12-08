package com.festin.app.adapter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CacheTestAdapter {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    public boolean canConnect() {
        try {
            String key = "test:connection:" + UUID.randomUUID();
            String value = "test-value";

            redisTemplate.opsForValue().set(key, value);
            String retrieved = redisTemplate.opsForValue().get(key);
            redisTemplate.delete(key);

            return value.equals(retrieved);
        } catch (Exception e) {
            return false;
        }
    }

    public void set(String key, String value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }
}
