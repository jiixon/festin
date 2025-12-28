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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 내 대기 목록 조회 Service
 *
 * 책임:
 * - Redis 대기열 (WAITING) + MySQL (CALLED) 조합
 * - 부스 정보, 순번, 예상 시간 조합
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
        List<MyWaitingListResult.WaitingItem> items = new ArrayList<>();

        // 1. Redis 대기열에 있는 부스 (WAITING 상태)
        Set<Long> activeBoothIds = queueCachePort.getUserActiveBooths(userId);
        for (Long boothId : activeBoothIds) {
            BoothInfo boothInfo = boothRepositoryPort.findBoothInfoById(boothId)
                    .orElse(null);

            if (boothInfo == null) {
                log.warn("부스 정보를 찾을 수 없습니다. userId={}, boothId={}", userId, boothId);
                continue; // 부스 정보 없으면 스킵
            }

            int position = queueCachePort.getPosition(boothId, userId).orElse(0);
            int totalWaiting = queueCachePort.getQueueSize(boothId);
            EstimatedWaitTime estimatedWaitTime = EstimatedWaitTime.fromWaitingCount(totalWaiting, boothInfo.capacity());

            LocalDateTime registeredAt = queueCachePort.getRegisteredAt(boothId, userId)
                    .orElse(LocalDateTime.now());

            if (position == 0 || registeredAt.equals(LocalDateTime.now())) {
                log.warn("대기 정보가 불완전합니다. userId={}, boothId={}, position={}", userId, boothId, position);
            }

            items.add(new MyWaitingListResult.WaitingItem(
                    boothId,
                    boothInfo.name(),
                    position,
                    totalWaiting,
                    estimatedWaitTime.minutes(),
                    null,  // WAITING 상태 (status = null)
                    registeredAt.format(DateTimeFormatter.ISO_DATE_TIME)
            ));
        }

        // 2. MySQL에 있는 부스 (CALLED 상태)
        List<Waiting> calledWaitings = waitingRepositoryPort.findActiveWaitingsByUserId(userId);
        for (Waiting waiting : calledWaitings) {
            BoothInfo boothInfo = boothRepositoryPort.findBoothInfoById(waiting.getBoothId())
                    .orElse(null);

            if (boothInfo == null) {
                log.warn("부스 정보를 찾을 수 없습니다. userId={}, boothId={}, waitingId={}",
                        userId, waiting.getBoothId(), waiting.getId());
                continue; // 부스 정보 없으면 스킵
            }

            // CALLED 상태는 이미 호출되었으므로 position은 calledPosition 사용
            int position = waiting.getCalledPosition() != null ? waiting.getCalledPosition() : 0;

            // CALLED 상태는 이미 호출되었으므로 totalWaiting은 의미 없음 (0)
            int totalWaiting = 0;

            // estimatedWaitTime은 0 (이미 호출되었으므로)
            int estimatedWaitTime = 0;

            items.add(new MyWaitingListResult.WaitingItem(
                    waiting.getBoothId(),
                    boothInfo.name(),
                    position,
                    totalWaiting,
                    estimatedWaitTime,
                    waiting.getStatus(),  // CALLED
                    waiting.getRegisteredAt().format(DateTimeFormatter.ISO_DATE_TIME)
            ));
        }

        return new MyWaitingListResult(items);
    }
}