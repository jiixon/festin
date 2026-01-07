package com.festin.app.waiting.domain.policy;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 노쇼 정책
 *
 * 비즈니스 규칙:
 * - 호출 후 5분 이내 입장하지 않으면 노쇼 처리
 * - 노쇼 시 자동으로 대기 완료 처리
 */
@Component
public class NoShowPolicy {

    /**
     * 노쇼 타임아웃 (5분)
     */
    private static final int NO_SHOW_TIMEOUT_SECONDS = 5 * 60;

    /**
     * 노쇼까지 남은 시간 계산 (초)
     *
     * @param calledAt 호출 시각
     * @param currentTime 현재 시각
     * @return 남은 시간 (초), 최소 0
     */
    public int calculateRemainingTime(LocalDateTime calledAt, LocalDateTime currentTime) {
        long elapsedSeconds = ChronoUnit.SECONDS.between(calledAt, currentTime);
        return Math.max(0, NO_SHOW_TIMEOUT_SECONDS - (int) elapsedSeconds);
    }

    /**
     * 노쇼 타임아웃 만료 여부 확인
     *
     * @param calledAt 호출 시각
     * @param currentTime 현재 시각
     * @return true: 타임아웃 만료 (노쇼 처리 대상), false: 아직 타임아웃 전
     */
    public boolean isTimeoutExpired(LocalDateTime calledAt, LocalDateTime currentTime) {
        return calculateRemainingTime(calledAt, currentTime) == 0;
    }

    /**
     * 노쇼 타임아웃 시간 반환 (초)
     *
     * @return 타임아웃 시간 (300초 = 5분)
     */
    public int getTimeoutSeconds() {
        return NO_SHOW_TIMEOUT_SECONDS;
    }
}