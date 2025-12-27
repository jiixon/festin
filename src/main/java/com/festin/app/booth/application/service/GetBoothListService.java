package com.festin.app.booth.application.service;

import com.festin.app.booth.application.port.in.GetBoothListUseCase;
import com.festin.app.booth.application.port.in.dto.BoothListResult;
import com.festin.app.booth.application.port.out.BoothRepositoryPort;
import com.festin.app.booth.application.port.out.dto.BoothInfo;
import com.festin.app.waiting.application.port.out.QueueCachePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 부스 목록 조회 Service
 */
@Service
@RequiredArgsConstructor
public class GetBoothListService implements GetBoothListUseCase {

    private static final int AVERAGE_EXPERIENCE_TIME_MINUTES = 5;

    private final BoothRepositoryPort boothRepositoryPort;
    private final QueueCachePort queueCachePort;

    @Override
    public BoothListResult getBoothList(Long universityId) {
        // 1. BoothInfo 목록 조회
        List<BoothInfo> boothInfoList = (universityId == null)
                ? boothRepositoryPort.findAllBoothInfo()
                : boothRepositoryPort.findAllBoothInfoByUniversityId(universityId);

        // 2. 각 부스별 대기 인원 및 예상 시간 조회
        List<BoothListResult.BoothItem> boothItems = boothInfoList.stream()
                .map(this::toBoothItem)
                .collect(Collectors.toList());

        return new BoothListResult(boothItems);
    }

    private BoothListResult.BoothItem toBoothItem(BoothInfo info) {
        // Redis에서 현재 대기 인원 조회
        int currentWaiting = queueCachePort.getQueueSize(info.id());

        // 예상 대기 시간 계산 (분 단위)
        int estimatedWaitTime = calculateEstimatedWaitTime(currentWaiting, info.capacity());

        return new BoothListResult.BoothItem(
                info.id(),
                info.name(),
                info.description(),
                info.universityName(),
                info.status(),
                info.capacity(),
                currentWaiting,
                estimatedWaitTime
        );
    }

    /**
     * 예상 대기 시간 계산
     *
     * 계산 로직:
     * - 한 번에 수용 가능한 인원: capacity
     * - 평균 체험 시간: 5분
     * - 예상 대기 시간 = (대기 인원 / 정원) * 평균 체험 시간
     */
    private int calculateEstimatedWaitTime(int waitingCount, int capacity) {
        if (waitingCount == 0 || capacity == 0) {
            return 0;
        }

        // 올림 처리: (waitingCount + capacity - 1) / capacity
        int rounds = (waitingCount + capacity - 1) / capacity;
        return rounds * AVERAGE_EXPERIENCE_TIME_MINUTES;
    }
}