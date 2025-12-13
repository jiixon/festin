package com.festin.app.application.service;

import com.festin.app.application.port.in.EnqueueUseCase;
import com.festin.app.application.port.in.command.EnqueueCommand;
import com.festin.app.application.port.in.result.EnqueueResult;
import com.festin.app.application.port.out.BoothCachePort;
import com.festin.app.application.port.out.BoothRepositoryPort;
import com.festin.app.application.port.out.QueueCachePort;
import com.festin.app.application.port.out.WaitingRepositoryPort;
import com.festin.app.domain.exception.BoothClosedException;
import com.festin.app.domain.exception.BoothNotFoundException;
import com.festin.app.domain.exception.QueueOperationException;
import com.festin.app.domain.model.Booth;
import com.festin.app.domain.model.BoothStatus;
import com.festin.app.domain.model.WaitingStatus;
import com.festin.app.domain.policy.MaxWaitingPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 대기 등록 UseCase 구현
 *
 * 비즈니스 흐름 (멱등성 보장):
 * 1. 부스 운영 상태 검증 (OPEN 여부)
 * 2. 중복 등록 체크 - 이미 등록되어 있으면 현재 정보 반환 (멱등성)
 * 3. 최대 대기 수 검증 (2개 제한) - 신규 등록인 경우에만
 * 4. Redis 대기열에 추가
 * 5. 순번 및 예상 대기 시간 계산
 */
@Service
@RequiredArgsConstructor
public class EnqueueService implements EnqueueUseCase {

    private final WaitingRepositoryPort waitingRepositoryPort;
    private final BoothRepositoryPort boothRepositoryPort;
    private final QueueCachePort queueCachePort;
    private final BoothCachePort boothCachePort;

    private final MaxWaitingPolicy maxWaitingPolicy;

    @Override
    @Transactional
    public EnqueueResult enqueue(EnqueueCommand command) {
        Long userId = command.userId();
        Long boothId = command.boothId();
        LocalDateTime now = LocalDateTime.now();

        // 1. 부스 조회 및 운영 상태 검증
        Booth booth = boothRepositoryPort.findById(boothId)
            .orElseThrow(() -> BoothNotFoundException.of(boothId));

        // Redis에서 실시간 운영 상태 확인
        BoothStatus status = boothCachePort.getStatus(boothId)
            .orElseThrow(BoothClosedException::new);

        if (status != BoothStatus.OPEN) {
            throw new BoothClosedException();
        }

        // 2. 멱등성 체크 - 이미 등록되어 있으면 기존 정보 반환
        Integer existingPosition = queueCachePort.getPosition(boothId, userId).orElse(null);

        if (existingPosition != null) {
            // 이미 등록되어 있음 - 현재 정보 반환 (멱등성 보장)
            int totalWaiting = queueCachePort.getQueueSize(boothId);
            int estimatedWaitTime = calculateEstimatedWaitTime(existingPosition);

            // Redis Score에서 원래 등록 시간 조회
            LocalDateTime registeredAt = queueCachePort.getRegisteredAt(boothId, userId)
                .orElse(now);  // 만약 조회 실패하면 현재 시간 사용

            return new EnqueueResult(
                boothId,
                booth.getName(),
                existingPosition,
                totalWaiting,
                estimatedWaitTime,
                registeredAt
            );
        }

        // 3. 최대 대기 수 검증 (2개 부스 제한) - 신규 등록인 경우에만
        int activeCount = waitingRepositoryPort.countActiveByUserId(userId, WaitingStatus.ACTIVE_STATUSES);
        maxWaitingPolicy.validate(activeCount);

        // 4. Redis 대기열에 추가 (신규 등록)
        queueCachePort.enqueue(boothId, userId, now);

        // 5. 순번 및 대기자 수 조회
        // TODO: Lua 스크립트로 원자성 보장 (enqueue + getPosition을 하나의 작업으로)
        Integer position = queueCachePort.getPosition(boothId, userId)
            .orElseThrow(QueueOperationException::enqueueFailed);
        int totalWaiting = queueCachePort.getQueueSize(boothId);

        // 6. 예상 대기 시간 계산
        int estimatedWaitTime = calculateEstimatedWaitTime(position);

        return new EnqueueResult(
            boothId,
            booth.getName(),
            position,
            totalWaiting,
            estimatedWaitTime,
            now
        );
    }

    /**
     * 예상 대기 시간 계산
     *
     * @param position 현재 순번
     * @return 예상 대기 시간 (분)
     */
    private int calculateEstimatedWaitTime(int position) {
        // 평균 체험 시간 20분 기준
        // TODO: 향후 부스별 평균 시간을 Redis에서 조회하도록 개선
        int avgTimePerPerson = 20;
        return (position - 1) * avgTimePerPerson;
    }
}