package com.festin.app.waiting.application.service;

import com.festin.app.booth.application.port.out.BoothCachePort;
import com.festin.app.waiting.application.port.in.ConfirmEntranceUseCase;
import com.festin.app.waiting.application.port.in.result.EntranceResult;
import com.festin.app.waiting.application.port.out.WaitingRepositoryPort;
import com.festin.app.waiting.domain.exception.WaitingNotFoundException;
import com.festin.app.waiting.domain.model.Waiting;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 입장 확인 Service
 *
 * 처리 흐름:
 * 1. DB에서 Waiting 조회
 * 2. boothId 일치 확인
 * 3. 도메인 로직 실행 (waiting.enter() - 상태 CALLED → ENTERED)
 * 4. DB 업데이트
 * 5. Redis current +1 (실제로 부스에 입장한 시점)
 * 6. 결과 반환
 */
@Service
@Transactional
public class ConfirmEntranceService implements ConfirmEntranceUseCase {

    private final WaitingRepositoryPort waitingRepositoryPort;
    private final BoothCachePort boothCachePort;

    public ConfirmEntranceService(
            WaitingRepositoryPort waitingRepositoryPort,
            BoothCachePort boothCachePort
    ) {
        this.waitingRepositoryPort = waitingRepositoryPort;
        this.boothCachePort = boothCachePort;
    }

    @Override
    public EntranceResult confirmEntrance(Long boothId, Long waitingId) {
        // DB에서 Waiting 조회
        Waiting waiting = waitingRepositoryPort.findById(waitingId)
                .orElseThrow(WaitingNotFoundException::new);

        // boothId 일치 확인
        if (!waiting.getBoothId().equals(boothId)) {
            throw new WaitingNotFoundException();
        }

        // 도메인 로직 실행 (상태 검증 포함: CALLED → ENTERED)
        waiting.enter();

        // DB 업데이트
        Waiting updatedWaiting = waitingRepositoryPort.save(waiting);

        // Redis 부스 현재 인원 +1 (실제 입장 시점)
        boothCachePort.incrementCurrentCount(boothId);

        // 결과 반환
        return new EntranceResult(
                updatedWaiting.getId(),
                updatedWaiting.getStatus(),
                updatedWaiting.getEnteredAt()
        );
    }
}