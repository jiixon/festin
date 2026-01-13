package com.festin.app.waiting.application.port.in.result;

import com.festin.app.waiting.domain.model.WaitingStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 호출 대기 목록 조회 결과
 *
 * Application Layer의 Result DTO
 */
public record CalledListResult(
        List<CalledItem> calledList) {

    /**
     * 호출된 대기 항목
     *
     * @param waitingId     대기 ID
     * @param userId        사용자 ID
     * @param nickname      사용자 닉네임
     * @param boothId       부스 ID
     * @param position      호출 순번
     * @param status        대기 상태
     * @param calledAt      호출 시간 (도메인 객체)
     * @param enteredAt     입장 시간 (CALLED면 null)
     * @param remainingTime 노쇼까지 남은 시간 (초), ENTERED면 null로 처리
     */
    public record CalledItem(
            Long waitingId,
            Long userId,
            String nickname,
            Long boothId,
            int position,
            WaitingStatus status,
            LocalDateTime calledAt,
            LocalDateTime enteredAt,
            Integer remainingTime) {
    }
}