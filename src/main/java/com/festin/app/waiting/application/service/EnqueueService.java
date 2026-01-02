package com.festin.app.waiting.application.service;

import com.festin.app.waiting.application.port.in.EnqueueUseCase;
import com.festin.app.waiting.application.port.in.command.EnqueueCommand;
import com.festin.app.waiting.application.port.in.result.EnqueueResult;
import com.festin.app.booth.application.port.out.BoothCachePort;
import com.festin.app.booth.domain.BoothNotFoundException;
import com.festin.app.booth.domain.model.Booth;
import com.festin.app.booth.domain.model.BoothStatus;
import com.festin.app.waiting.application.port.out.QueueCachePort;
import com.festin.app.waiting.application.port.out.QueueCachePort.EnqueueAtomicResult;
import com.festin.app.waiting.application.port.out.QueueCachePort.EnqueueStatus;
import com.festin.app.waiting.domain.exception.MaxWaitingExceededException;
import com.festin.app.waiting.domain.model.EstimatedWaitTime;
import com.festin.app.waiting.domain.policy.MaxWaitingPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 대기 등록 UseCase 구현
 *
 * 비즈니스 흐름 (멱등성 보장 + Race Condition 방지):
 * 1. 부스 운영 상태 검증 (OPEN 여부)
 * 2. Lua Script로 원자적 등록 처리
 *    - 중복 등록 체크 (멱등성)
 *    - 최대 대기 수 검증 (2개 제한)
 *    - Redis 대기열에 추가
 *    - 활성 부스 목록에 추가
 * 3. 결과에 따라 적절한 응답 반환
 */
@Service
@RequiredArgsConstructor
public class EnqueueService implements EnqueueUseCase {

    private final QueueCachePort queueCachePort;
    private final BoothCachePort boothCachePort;

    private final MaxWaitingPolicy maxWaitingPolicy;

    @Override
    @Transactional
    public EnqueueResult enqueue(EnqueueCommand command) {
        Long userId = command.userId();
        Long boothId = command.boothId();
        LocalDateTime now = LocalDateTime.now();

        // Redis에서 부스 실시간 정보 조회 (DB 조회하지 않음)
        BoothStatus status = boothCachePort.getStatus(boothId)
            .orElseThrow(BoothNotFoundException::new);
        Integer capacity = boothCachePort.getCapacity(boothId)
            .orElseThrow(BoothNotFoundException::new);
        String boothName = boothCachePort.getName(boothId)
            .orElseThrow(BoothNotFoundException::new);

        // Booth 도메인 객체 생성 (Redis 데이터 기반)
        Booth booth = Booth.of(
                boothId,
                boothName,
                capacity,
                status
        );

        // 운영 상태 검증 (Booth 도메인 로직)
        booth.validateForEnqueue();

        // Lua Script로 원자적 등록 처리
        // - 중복 체크, 최대 부스 수 검증, enqueue, addActiveBooth를 원자적으로 처리
        // - Redis 단일 스레드 특성으로 Race Condition 완전 방지
        EnqueueAtomicResult result = queueCachePort.enqueueAtomic(
                boothId,
                userId,
                now,
                maxWaitingPolicy.getMaxWaitingBooths()
        );

        // 상태별 처리
        return switch (result.status()) {
            case SUCCESS -> {
                // 신규 등록 성공
                EstimatedWaitTime estimatedWaitTime = EstimatedWaitTime.fromPosition(result.position());
                yield new EnqueueResult(
                        boothId,
                        boothName,
                        result.position(),
                        result.totalWaiting(),
                        estimatedWaitTime.minutes(),
                        now
                );
            }
            case ALREADY_ENQUEUED -> {
                // 이미 등록되어 있음 (멱등성)
                EstimatedWaitTime estimatedWaitTime = EstimatedWaitTime.fromPosition(result.position());

                // 원래 등록 시간 조회
                LocalDateTime registeredAt = queueCachePort.getRegisteredAt(boothId, userId)
                        .orElse(now);

                yield new EnqueueResult(
                        boothId,
                        boothName,
                        result.position(),
                        result.totalWaiting(),
                        estimatedWaitTime.minutes(),
                        registeredAt
                );
            }
            case MAX_BOOTHS_EXCEEDED -> {
                // 최대 활성 부스 수 초과 (2개 제한)
                throw new MaxWaitingExceededException();
            }
        };
    }
}