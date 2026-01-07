package com.festin.app.waiting.application.service;

import com.festin.app.waiting.application.port.in.GetPositionUseCase;
import com.festin.app.waiting.application.port.in.result.PositionResult;
import com.festin.app.booth.application.port.out.BoothCachePort;
import com.festin.app.waiting.application.port.out.QueueCachePort;
import com.festin.app.booth.domain.BoothNotFoundException;
import com.festin.app.waiting.domain.exception.WaitingNotFoundException;
import com.festin.app.waiting.domain.model.EstimatedWaitTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 순번 조회 UseCase 구현
 *
 * 비즈니스 흐름:
 * 1. 대기열에서 사용자 위치 조회
 * 2. 대기 중이 아니면 예외 발생
 * 3. 전체 대기자 수 계산
 * 4. 예상 대기 시간 계산
 */
@Service
@RequiredArgsConstructor
public class GetPositionService implements GetPositionUseCase {

    private final BoothCachePort boothCachePort;
    private final QueueCachePort queueCachePort;

    @Override
    @Transactional(readOnly = true)
    public PositionResult getPosition(Long userId, Long boothId) {
        String boothName = boothCachePort.getName(boothId)
            .orElseThrow(BoothNotFoundException::new);

        Integer position = queueCachePort.getPosition(boothId, userId)
            .orElseThrow(WaitingNotFoundException::new);

        int totalWaiting = queueCachePort.getQueueSize(boothId);

        EstimatedWaitTime estimatedWaitTime = EstimatedWaitTime.fromPosition(position);

        return new PositionResult(
            boothId,
            boothName,
            position,
            totalWaiting,
            estimatedWaitTime.minutes()
        );
    }
}