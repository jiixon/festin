package com.festin.app.application.port.out;

/**
 * 멱등성 캐시 Port
 *
 * 책임:
 * - 중복 요청 방지를 위한 멱등성 키 관리
 * - 24시간 TTL로 자동 만료
 *
 * 구현체:
 * - RedisIdempotencyAdapter
 *
 * Redis 키 형식:
 * - idempotency:{userId}:{boothId}:{date} (String)
 * - TTL: 24시간
 */
public interface IdempotencyCachePort {

    /**
     * 멱등성 키 존재 여부 확인
     *
     * @param key 멱등성 키
     * @return 존재 여부
     */
    boolean exists(String key);

    /**
     * 멱등성 키 저장
     *
     * @param key 멱등성 키
     * @param ttlSeconds TTL (초 단위)
     */
    void save(String key, long ttlSeconds);

    /**
     * 멱등성 키 삭제
     *
     * @param key 멱등성 키
     */
    void delete(String key);
}
