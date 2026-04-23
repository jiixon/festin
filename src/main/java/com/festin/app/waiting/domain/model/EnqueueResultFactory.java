package com.festin.app.waiting.domain.model;

import com.festin.app.booth.domain.model.Booth;
import com.festin.app.waiting.application.port.in.result.EnqueueResult;
import com.festin.app.waiting.application.port.out.QueueCachePort;
import com.festin.app.waiting.domain.exception.MaxWaitingExceededException;

import java.time.LocalDateTime;

/**
 * EnqueueResult 생성 Factory
 *
 * 책임:
 * - EnqueueAtomicResult를 EnqueueResult로 변환
 * - 각 상태(SUCCESS, ALREADY_ENQUEUED, MAX_BOOTHS_EXCEEDED)별 처리 로직
 *
 * 멱등성 설계:
 * - SUCCESS → 201 Created (신규 등록)
 * - ALREADY_ENQUEUED → 200 OK (기존 등록 반환, 큐 중복 없음)
 * - MAX_BOOTHS_EXCEEDED → 409 Conflict (예외)
 */
public class EnqueueResultFactory {

    public static EnqueueResult from(
            QueueCachePort.EnqueueAtomicResult atomicResult,
            Booth booth,
            LocalDateTime requestedAt,
            QueueCachePort queueCachePort,
            Long boothId,
            Long userId
    ) {
        return switch (atomicResult.status()) {
            case SUCCESS -> createSuccess(atomicResult, booth, requestedAt);
            case ALREADY_ENQUEUED -> createAlreadyEnqueued(atomicResult, booth, queueCachePort, boothId, userId);
            case MAX_BOOTHS_EXCEEDED -> throw new MaxWaitingExceededException();
        };
    }

    private static EnqueueResult createSuccess(
            QueueCachePort.EnqueueAtomicResult atomicResult,
            Booth booth,
            LocalDateTime registeredAt
    ) {
        EstimatedWaitTime estimatedWaitTime = EstimatedWaitTime.fromPosition(atomicResult.position());
        return new EnqueueResult(
                booth.getId(),
                booth.getName(),
                atomicResult.position(),
                atomicResult.totalWaiting(),
                estimatedWaitTime.minutes(),
                registeredAt,
                false
        );
    }

    private static EnqueueResult createAlreadyEnqueued(
            QueueCachePort.EnqueueAtomicResult atomicResult,
            Booth booth,
            QueueCachePort queueCachePort,
            Long boothId,
            Long userId
    ) {
        EstimatedWaitTime estimatedWaitTime = EstimatedWaitTime.fromPosition(atomicResult.position());
        LocalDateTime originalRegisteredAt = queueCachePort.getRegisteredAt(boothId, userId)
                .orElse(LocalDateTime.now());

        return new EnqueueResult(
                booth.getId(),
                booth.getName(),
                atomicResult.position(),
                atomicResult.totalWaiting(),
                estimatedWaitTime.minutes(),
                originalRegisteredAt,
                true
        );
    }
}