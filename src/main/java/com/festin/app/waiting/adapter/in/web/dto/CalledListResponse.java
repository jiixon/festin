package com.festin.app.waiting.adapter.in.web.dto;

import com.festin.app.waiting.application.port.in.result.CalledListResult;
import com.festin.app.waiting.domain.model.WaitingStatus;

import java.time.format.DateTimeFormatter;
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
     *
     * 책임:
     * - LocalDateTime → ISO-8601 String 포맷팅 (Presentation Layer)
     * - WaitingStatus enum → String 변환
     */
    public static CalledListResponse from(CalledListResult result) {
        List<CalledItem> items = result.calledList().stream()
            .map(item -> new CalledItem(
                item.waitingId(),
                item.userId(),
                item.nickname(),
                item.position(),
                item.status().name(),
                item.calledAt().format(DateTimeFormatter.ISO_DATE_TIME),
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
        String status,
        String calledAt,
        int remainingTime
    ) {}
}