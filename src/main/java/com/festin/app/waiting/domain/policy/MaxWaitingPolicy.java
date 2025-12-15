package com.festin.app.waiting.domain.policy;

import com.festin.app.waiting.domain.exception.MaxWaitingExceededException;
import org.springframework.stereotype.Component;

/**
 * 최대 대기 정책
 *
 * 비즈니스 규칙:
 * - 사용자는 최대 2개 부스까지만 동시 대기 가능
 * - 공정한 기회 제공 및 독점 방지
 */
@Component
public class MaxWaitingPolicy {
    private static final int MAX_ACTIVE_BOOTHS = 2;

    /**
     * 최대 대기 가능 부스 수 검증
     *
     * @param currentActiveBooths 현재 대기 중인 부스 수
     * @throws MaxWaitingExceededException 최대 2개 초과 시
     */
    public void validate(int currentActiveBooths) {
        if (currentActiveBooths >= MAX_ACTIVE_BOOTHS) {
            throw new MaxWaitingExceededException();
        }
    }
}
