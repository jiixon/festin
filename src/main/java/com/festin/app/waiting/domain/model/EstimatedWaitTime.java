package com.festin.app.waiting.domain.model;

/**
 * 예상 대기 시간 Value Object
 *
 * 책임:
 * - 대기 시간 계산 로직 캡슐화
 * - 비즈니스 규칙: 평균 체험 시간 10분 기준
 *
 * 설계:
 * - 정적 팩토리 메서드를 통해서만 생성
 * - 정적 팩토리에서 0 이상 보장하므로 별도 검증 불필요
 */
public record EstimatedWaitTime(int minutes) {

    private static final int AVERAGE_EXPERIENCE_TIME_MINUTES = 10;

    /**
     * 대기 인원과 수용 인원 기반으로 예상 대기 시간 계산
     *
     * 비즈니스 규칙:
     * - 필요한 라운드 수 = (대기 인원 / 수용 인원) 올림
     * - 예상 시간 = 라운드 수 × 평균 체험 시간 (10분)
     *
     * @param waitingCount 대기 인원
     * @param capacity 수용 인원
     * @return 예상 대기 시간
     */
    public static EstimatedWaitTime fromWaitingCount(int waitingCount, int capacity) {
        if (waitingCount == 0 || capacity == 0) {
            return new EstimatedWaitTime(0);
        }

        // 필요한 라운드 수 계산 (올림)
        int rounds = (waitingCount + capacity - 1) / capacity;

        // 각 라운드당 평균 체험 시간 적용
        return new EstimatedWaitTime(rounds * AVERAGE_EXPERIENCE_TIME_MINUTES);
    }

    /**
     * 순번 기반으로 예상 대기 시간 계산
     *
     * 비즈니스 규칙:
     * - 예상 시간 = 순번 × 평균 체험 시간 (10분)
     * - 순번이 1이면 10분, 2면 20분...
     *
     * @param position 순번 (1부터 시작)
     * @return 예상 대기 시간
     */
    public static EstimatedWaitTime fromPosition(int position) {
        if (position <= 0) {
            return new EstimatedWaitTime(0);
        }

        return new EstimatedWaitTime(position * AVERAGE_EXPERIENCE_TIME_MINUTES);
    }

    /**
     * 0분 (대기 없음)
     */
    public static EstimatedWaitTime zero() {
        return new EstimatedWaitTime(0);
    }
}