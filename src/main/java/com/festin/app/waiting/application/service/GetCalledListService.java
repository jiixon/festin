package com.festin.app.waiting.application.service;

import com.festin.app.waiting.application.port.in.GetCalledListUseCase;
import com.festin.app.waiting.application.port.in.result.CalledListResult;
import com.festin.app.waiting.application.port.in.result.CalledListResult.CalledItem;
import com.festin.app.waiting.application.port.out.WaitingRepositoryPort;
import com.festin.app.waiting.application.port.out.dto.CalledWaitingInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 호출 대기 목록 조회 Service
 *
 * 책임:
 * - 부스의 호출된 대기 목록 조회
 * - 각 대기 건의 남은 시간 계산 (노쇼 타임아웃 기준)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetCalledListService implements GetCalledListUseCase {

    /**
     * 노쇼 타임아웃 (5분)
     */
    private static final int NO_SHOW_TIMEOUT_SECONDS = 5 * 60;

    private final WaitingRepositoryPort waitingRepositoryPort;

    /**
     * 부스의 호출된 대기 목록 조회
     *
     * 1. 호출된 대기 목록 조회 (User 정보 포함)
     * 2. 각 대기 건의 남은 시간 계산
     * 3. Result 변환
     *
     * @param boothId 부스 ID
     * @return 호출된 대기 목록 (남은 시간 포함)
     */
    @Override
    public CalledListResult getCalledList(Long boothId) {
        // 1. 호출된 대기 목록 조회 (User 정보 포함)
        List<CalledWaitingInfo> calledWaitings =
            waitingRepositoryPort.findCalledByBoothIdWithUserInfo(boothId);

        // 2. 남은 시간 계산 및 Result 변환
        LocalDateTime now = LocalDateTime.now();
        List<CalledItem> items = calledWaitings.stream()
            .map(info -> {
                // 경과 시간 계산 (초)
                long elapsedSeconds = ChronoUnit.SECONDS.between(info.calledAt(), now);

                // 남은 시간 = max(0, 타임아웃 - 경과시간)
                int remainingSeconds = Math.max(0, NO_SHOW_TIMEOUT_SECONDS - (int) elapsedSeconds);

                return new CalledItem(
                    info.waitingId(),
                    info.userId(),
                    info.nickname(),
                    info.position(),
                    info.status(),
                    info.calledAt().format(DateTimeFormatter.ISO_DATE_TIME),
                    remainingSeconds
                );
            })
            .toList();

        return new CalledListResult(items);
    }
}