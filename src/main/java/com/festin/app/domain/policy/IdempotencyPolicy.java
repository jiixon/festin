package com.festin.app.domain.policy;

import com.festin.app.domain.exception.AlreadyRegisteredException;

/**
 * 멱등성 정책
 *
 * 비즈니스 규칙:
 * - 당일 내 같은 부스 재등록 불가
 * - 멱등성 키로 중복 요청 방지 (TTL 24시간)
 *
 * 멱등성 키 형식:
 * - "idempotency:userId:{userId}:boothId:{boothId}:date:{yyyyMMdd}"
 */
public class IdempotencyPolicy {

    /**
     * 멱등성 검증
     *
     * @param keyExists 멱등성 키가 이미 존재하는지 여부
     * @throws AlreadyRegisteredException 이미 등록된 경우 (재등록 시도)
     */
    public void validate(boolean keyExists) {
        if (keyExists) {
            throw new AlreadyRegisteredException();
        }
    }
}