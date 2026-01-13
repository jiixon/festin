package com.festin.app.waiting.application.port.out.dto;

import com.festin.app.waiting.domain.model.WaitingStatus;

import java.time.LocalDateTime;

/**
 * 호출된 대기 정보 DTO (Read Model)
 *
 * CQRS 패턴:
 * - 읽기 전용 쿼리에 특화된 DTO
 * - User 정보(nickname)를 포함
 * - Domain 모델(Waiting)은 userId만 가지므로, 조회 시 JOIN FETCH로 User 정보 포함
 */
public record CalledWaitingInfo(
        Long waitingId,
        Long userId,
        String nickname,
        Long boothId,
        int position,
        WaitingStatus status,
        LocalDateTime calledAt,
        LocalDateTime enteredAt) {
}
