package com.festin.app.application.port.out;

import com.festin.app.domain.model.BoothStatus;

import java.util.Optional;

/**
 * 부스 상태 캐시 Port
 *
 * 책임:
 * - 부스 현재 인원 실시간 관리 (Redis)
 * - 부스 운영 상태 관리 (OPEN/CLOSED)
 * - 부스 정원 관리
 *
 * 구현체:
 * - RedisBoothAdapter
 *
 * Redis 키 형식:
 * - booth:{boothId}:current (String) - 현재 인원
 * - booth:{boothId}:status (String) - 운영 상태
 * - booth:{boothId}:capacity (String) - 최대 정원
 */
public interface BoothCachePort {

    /**
     * 부스 현재 인원 증가
     *
     * @param boothId 부스 ID
     * @return 증가 후 현재 인원
     */
    int incrementCurrentCount(Long boothId);

    /**
     * 부스 현재 인원 감소
     *
     * @param boothId 부스 ID
     * @return 감소 후 현재 인원
     */
    int decrementCurrentCount(Long boothId);

    /**
     * 부스 현재 인원 조회
     *
     * @param boothId 부스 ID
     * @return 현재 인원
     */
    int getCurrentCount(Long boothId);

    /**
     * 부스 운영 상태 저장
     *
     * @param boothId 부스 ID
     * @param status 운영 상태
     */
    void setStatus(Long boothId, BoothStatus status);

    /**
     * 부스 운영 상태 조회
     *
     * @param boothId 부스 ID
     * @return 운영 상태
     */
    Optional<BoothStatus> getStatus(Long boothId);

    /**
     * 부스 정원 저장
     *
     * @param boothId 부스 ID
     * @param capacity 최대 정원
     */
    void setCapacity(Long boothId, int capacity);

    /**
     * 부스 정원 조회
     *
     * @param boothId 부스 ID
     * @return 최대 정원
     */
    Optional<Integer> getCapacity(Long boothId);
}
