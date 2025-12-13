package com.festin.app.application.service;

import com.festin.app.application.port.in.GetPositionUseCase;
import com.festin.app.application.port.in.result.PositionResult;
import com.festin.app.application.port.out.BoothRepositoryPort;
import com.festin.app.application.port.out.QueueCachePort;
import com.festin.app.domain.exception.BoothNotFoundException;
import com.festin.app.domain.exception.WaitingNotFoundException;
import com.festin.app.domain.model.Booth;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 순번 조회 UseCase 구현
 *
 * 비즈니스 흐름:
 * 1. Redis 대기열에서 사용자 위치 조회
 * 2. 대기 중이 아니면 예외 발생
 * 3. 전체 대기자 수 계산
 * 4. 예상 대기 시간 계산
 */
@Service
@RequiredArgsConstructor
public class GetPositionService implements GetPositionUseCase {

    private final BoothRepositoryPort boothRepositoryPort;
    private final QueueCachePort queueCachePort;

    @Override
    @Transactional(readOnly = true)
    public PositionResult getPosition(Long userId, Long boothId) {
        // 1. 부스 조회
        Booth booth = boothRepositoryPort.findById(boothId)
            .orElseThrow(() -> BoothNotFoundException.of(boothId));

        // 2. Redis 대기열에서 순번 조회
        Integer position = queueCachePort.getPosition(boothId, userId)
            .orElseThrow(WaitingNotFoundException::new);

        // 3. 전체 대기자 수 조회
        int totalWaiting = queueCachePort.getQueueSize(boothId);

        // 4. 예상 대기 시간 계산 (부스별 평균 체험 시간 * 앞 대기자 수)
        // TODO: 부스별 평균 체험 시간은 Redis에 저장된 값 사용 (현재는 10분 고정)
        int estimatedWaitTime = (position - 1) * 10;

        return new PositionResult(
            boothId,
            booth.getName(),
            position,
            totalWaiting,
            estimatedWaitTime
        );
    }
}