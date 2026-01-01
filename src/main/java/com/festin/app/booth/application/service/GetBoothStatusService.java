package com.festin.app.booth.application.service;

import com.festin.app.booth.application.port.in.GetBoothStatusUseCase;
import com.festin.app.booth.application.port.in.dto.BoothStatusResult;
import com.festin.app.booth.application.port.in.dto.BoothStatusResult.TodayStats;
import com.festin.app.booth.application.port.out.BoothCachePort;
import com.festin.app.booth.domain.BoothNotFoundException;
import com.festin.app.waiting.application.port.out.QueueCachePort;
import com.festin.app.waiting.application.port.out.WaitingRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 부스 현황 조회 Service (스태프용)
 *
 * 책임:
 * - Redis에서 실시간 정보 조회 (currentPeople, capacity, totalWaiting, boothName)
 * - MySQL에서 오늘 통계 조회 (totalCalled, totalEntered, totalNoShow, totalCompleted)
 * - 두 데이터 소스를 조합하여 BoothStatusResult 반환
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetBoothStatusService implements GetBoothStatusUseCase {

    private final BoothCachePort boothCachePort;
    private final QueueCachePort queueCachePort;
    private final WaitingRepositoryPort waitingRepositoryPort;

    @Override
    public BoothStatusResult getBoothStatus(Long boothId) {
        // 1. Redis에서 실시간 데이터 조회
        String boothName = boothCachePort.getName(boothId)
                .orElseThrow(BoothNotFoundException::new);
        int currentPeople = boothCachePort.getCurrentCount(boothId);
        Integer capacity = boothCachePort.getCapacity(boothId)
                .orElseThrow(BoothNotFoundException::new);
        int totalWaiting = queueCachePort.getQueueSize(boothId);

        // 2. MySQL에서 오늘 통계 조회
        LocalDate today = LocalDate.now();
        int totalCalled = waitingRepositoryPort.countTodayCalledByBoothId(boothId, today);
        int totalEntered = waitingRepositoryPort.countTodayEnteredByBoothId(boothId, today);
        int totalNoShow = waitingRepositoryPort.countTodayNoShowByBoothId(boothId, today);
        int totalCompleted = waitingRepositoryPort.countTodayCompletedByBoothId(boothId, today);

        TodayStats todayStats = new TodayStats(
            totalCalled, totalEntered, totalNoShow, totalCompleted
        );

        return new BoothStatusResult(
            boothId, boothName, currentPeople, capacity, totalWaiting, todayStats
        );
    }
}
