package com.festin.app.waiting.application.service;

import com.festin.app.booth.application.port.out.BoothRepositoryPort;
import com.festin.app.booth.application.port.out.dto.BoothInfo;
import com.festin.app.waiting.application.port.in.GetMyWaitingListUseCase;
import com.festin.app.waiting.application.port.in.result.MyWaitingListResult;
import com.festin.app.waiting.application.port.out.QueueCachePort;
import com.festin.app.waiting.application.port.out.WaitingRepositoryPort;
import com.festin.app.waiting.domain.model.EstimatedWaitTime;
import com.festin.app.waiting.domain.model.Waiting;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

/**
 * 내 대기 목록 조회 Service
 *
 * 책임:
 * - Redis 대기열 (WAITING) + MySQL (CALLED) 조합
 * - WaitingItem factory 메서드를 통한 변환 위임
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetMyWaitingListService implements GetMyWaitingListUseCase {

    private final QueueCachePort queueCachePort;
    private final WaitingRepositoryPort waitingRepositoryPort;
    private final BoothRepositoryPort boothRepositoryPort;

    @Override
    public MyWaitingListResult getMyWaitingList(Long userId) {
        // 1. Redis 대기열 (WAITING) + 2. MySQL 호출 대기 (CALLED) 조합
        List<MyWaitingListResult.WaitingItem> items = Stream.concat(
                collectRedisWaitings(userId),
                collectCalledWaitings(userId)
        ).toList();

        return new MyWaitingListResult(items);
    }

    /**
     * Redis 대기열에서 WaitingItem 수집
     */
    private Stream<MyWaitingListResult.WaitingItem> collectRedisWaitings(Long userId) {
        return queueCachePort.getUserActiveBooths(userId).stream()
                .map(boothId -> {
                    BoothInfo boothInfo = boothRepositoryPort.findBoothInfoById(boothId).orElse(null);
                    if (boothInfo == null) {
                        log.warn("부스 정보를 찾을 수 없습니다. userId={}, boothId={}", userId, boothId);
                        return null;
                    }

                    int position = queueCachePort.getPosition(boothId, userId).orElse(0);
                    int totalWaiting = queueCachePort.getQueueSize(boothId);
                    EstimatedWaitTime estimatedWaitTime = EstimatedWaitTime.fromWaitingCount(totalWaiting, boothInfo.capacity());
                    LocalDateTime registeredAt = queueCachePort.getRegisteredAt(boothId, userId).orElse(LocalDateTime.now());

                    if (position == 0) {
                        log.warn("대기 정보가 불완전합니다. userId={}, boothId={}, position={}", userId, boothId, position);
                    }

                    return MyWaitingListResult.WaitingItem.fromRedisQueue(
                            boothId,
                            boothInfo.name(),
                            position,
                            totalWaiting,
                            estimatedWaitTime.minutes(),
                            registeredAt
                    );
                })
                .filter(item -> item != null);
    }

    /**
     * MySQL에서 호출된 Waiting 수집
     */
    private Stream<MyWaitingListResult.WaitingItem> collectCalledWaitings(Long userId) {
        return waitingRepositoryPort.findActiveWaitingsByUserId(userId).stream()
                .map(waiting -> {
                    BoothInfo boothInfo = boothRepositoryPort.findBoothInfoById(waiting.getBoothId()).orElse(null);
                    if (boothInfo == null) {
                        log.warn("부스 정보를 찾을 수 없습니다. userId={}, boothId={}, waitingId={}",
                                userId, waiting.getBoothId(), waiting.getId());
                        return null;
                    }

                    return MyWaitingListResult.WaitingItem.fromCalled(waiting, boothInfo.name());
                })
                .filter(item -> item != null);
    }
}