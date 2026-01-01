package com.festin.app.waiting.adapter.in.web.dto;

import com.festin.app.waiting.application.port.in.result.CalledListResult;
import com.festin.app.waiting.domain.model.WaitingStatus;

import java.util.List;

/**
 * 호출 대기 목록 조회 Response
 *
 * Web Layer DTO
 */
public record CalledListResponse(
    List<CalledItem> calledList
) {

    /**
     * CalledListResult → CalledListResponse 변환
     */
    public static CalledListResponse from(CalledListResult result) {
        List<CalledItem> items = result.calledList().stream()
            .map(item -> new CalledItem(
                item.waitingId(),
                item.userId(),
                item.nickname(),
                item.position(),
                item.status(),
                item.calledAt(),
                item.remainingTime()
            ))
            .toList();

        return new CalledListResponse(items);
    }

    /**
     * 호출된 대기 항목
     */
    public record CalledItem(
        Long waitingId,
        Long userId,
        String nickname,
        int position,
        WaitingStatus status,
        String calledAt,
        int remainingTime
    ) {}
}