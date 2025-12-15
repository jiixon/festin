package com.festin.waiting.domain.policy;

import com.festin.booth.domain.BoothFullException;

/**
 * 부스 정원 정책
 *
 * 비즈니스 규칙:
 * - 부스 현재 인원이 최대 정원에 도달하면 호출 불가
 * - 동시성 제어로 정원 초과 방지 필요
 */
public class BoothCapacityPolicy {

    /**
     * 부스 정원 검증
     *
     * @param currentCount 현재 입장 인원
     * @param capacity 최대 수용 인원
     * @throws BoothFullException 정원 초과 시
     */
    public void validate(int currentCount, int capacity) {
        if (currentCount >= capacity) {
            throw new BoothFullException();
        }
    }
}
