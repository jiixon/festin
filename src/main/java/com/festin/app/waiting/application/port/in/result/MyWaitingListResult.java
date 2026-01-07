package com.festin.app.waiting.application.port.in.result;

import com.festin.app.waiting.domain.model.Waiting;
import com.festin.app.waiting.domain.model.WaitingStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 내 대기 목록 조회 결과
 */
public record MyWaitingListResult(
        List<WaitingItem> waitings
) {
    /**
     * 대기 항목
     *
     * status:
     * - null: WAITING (대기 중, 아직 호출 안됨)
     * - CALLED: 호출됨 (입장 대기 중)
     */
    public record WaitingItem(
            Long boothId,
            String boothName,
            int position,
            int totalWaiting,
            int estimatedWaitTime,
            WaitingStatus status,      // null = WAITING, CALLED = 호출됨
            LocalDateTime registeredAt // 도메인 객체
    ) {
        /**
         * Redis 대기열로부터 WaitingItem 생성
         *
         * @param boothId 부스 ID
         * @param boothName 부스 이름
         * @param position 순번
         * @param totalWaiting 총 대기 인원
         * @param estimatedWaitTime 예상 대기 시간 (분)
         * @param registeredAt 등록 시각
         * @return WAITING 상태의 WaitingItem
         */
        public static WaitingItem fromRedisQueue(
                Long boothId,
                String boothName,
                int position,
                int totalWaiting,
                int estimatedWaitTime,
                LocalDateTime registeredAt
        ) {
            return new WaitingItem(
                    boothId,
                    boothName,
                    position,
                    totalWaiting,
                    estimatedWaitTime,
                    null,  // WAITING 상태 (Redis)
                    registeredAt
            );
        }

        /**
         * 호출된 Waiting으로부터 WaitingItem 생성
         *
         * @param waiting 호출된 Waiting (CALLED 상태)
         * @param boothName 부스 이름
         * @return CALLED 상태의 WaitingItem
         */
        public static WaitingItem fromCalled(Waiting waiting, String boothName) {
            int position = waiting.getCalledPosition() != null ? waiting.getCalledPosition() : 0;

            return new WaitingItem(
                    waiting.getBoothId(),
                    boothName,
                    position,
                    0,  // CALLED 상태는 totalWaiting 의미 없음
                    0,  // CALLED 상태는 estimatedWaitTime 의미 없음
                    waiting.getStatus(),  // CALLED
                    waiting.getRegisteredAt()
            );
        }
    }
}