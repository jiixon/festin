package com.festin.app.application.service;

import com.festin.app.application.port.in.EnqueueUseCase;
import com.festin.app.application.port.in.command.EnqueueCommand;
import com.festin.app.application.port.in.result.EnqueueResult;
import com.festin.app.application.port.out.*;
import com.festin.app.domain.exception.BoothClosedException;
import com.festin.app.domain.exception.BoothNotFoundException;
import com.festin.app.domain.exception.QueueOperationException;
import com.festin.app.domain.model.Booth;
import com.festin.app.domain.model.BoothStatus;
import com.festin.app.domain.model.WaitingStatus;
import com.festin.app.domain.policy.DuplicateRegistrationPolicy;
import com.festin.app.domain.policy.IdempotencyPolicy;
import com.festin.app.domain.policy.MaxWaitingPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 대기 등록 UseCase 구현
 *
 * 비즈니스 흐름:
 * 1. 멱등성 검증 (당일 내 재등록 방지)
 * 2. 최대 대기 수 검증 (2개 제한)
 * 3. 중복 등록 검증 (같은 부스 중복 방지)
 * 4. 부스 운영 상태 검증 (OPEN 여부)
 * 5. Redis 대기열에 추가
 * 6. 순번 및 예상 대기 시간 계산
 */
@Service
@RequiredArgsConstructor
public class EnqueueService implements EnqueueUseCase {

    private final WaitingRepositoryPort waitingRepositoryPort;
    private final BoothRepositoryPort boothRepositoryPort;
    private final QueueCachePort queueCachePort;
    private final BoothCachePort boothCachePort;
    private final IdempotencyCachePort idempotencyCachePort;

    private final MaxWaitingPolicy maxWaitingPolicy;
    private final DuplicateRegistrationPolicy duplicateRegistrationPolicy;
    private final IdempotencyPolicy idempotencyPolicy;

    @Override
    @Transactional
    public EnqueueResult enqueue(EnqueueCommand command) {
        Long userId = command.userId();
        Long boothId = command.boothId();
        LocalDateTime now = LocalDateTime.now();

        // 1. 멱등성 검증 (userId-boothId-date 조합으로 당일 재등록 방지)
        String idempotencyKey = idempotencyPolicy.generateKey(userId, boothId);
        idempotencyPolicy.validate(idempotencyKey, idempotencyCachePort);

        // 2. 최대 대기 수 검증 (2개 부스 제한)
        int activeCount = waitingRepositoryPort.countActiveByUserId(userId, WaitingStatus.ACTIVE_STATUSES);
        maxWaitingPolicy.validate(activeCount);

        // 3. 중복 등록 검증 (같은 부스에 이미 대기 중인지)
        boolean alreadyInQueue = queueCachePort.getPosition(boothId, userId).isPresent();
        duplicateRegistrationPolicy.validate(alreadyInQueue);

        // 4. 부스 조회 및 운영 상태 검증
        Booth booth = boothRepositoryPort.findById(boothId)
            .orElseThrow(() -> BoothNotFoundException.of(boothId));

        // Redis에서 실시간 운영 상태 확인
        BoothStatus status = boothCachePort.getStatus(boothId)
            .orElseThrow(BoothClosedException::new);

        if (status != BoothStatus.OPEN) {
            throw new BoothClosedException();
        }

        // 5. Redis 대기열에 추가
        queueCachePort.enqueue(boothId, userId, now);

        // 6. 멱등성 키 저장 (24시간 TTL)
        idempotencyCachePort.save(idempotencyKey, 24 * 60 * 60);

        // 7. 순번 및 대기자 수 조회
        // TODO: Lua 스크립트로 원자성 보장 (enqueue + getPosition을 하나의 작업으로)
        Integer position = queueCachePort.getPosition(boothId, userId)
            .orElseThrow(QueueOperationException::enqueueFailed);
        int totalWaiting = queueCachePort.getQueueSize(boothId);

        // 8. 예상 대기 시간 계산 (부스별 평균 체험 시간 * 앞 대기자 수)
        // TODO: 부스별 평균 체험 시간은 Redis에 저장된 값 사용 (현재는 10분 고정)
        int estimatedWaitTime = (position - 1) * 10;

        return new EnqueueResult(
            boothId,
            booth.getName(),
            position,
            totalWaiting,
            estimatedWaitTime,
            now
        );
    }
}