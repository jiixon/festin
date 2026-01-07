package com.festin.app.waiting.application.service;

import com.festin.app.booth.application.port.out.BoothCachePort;
import com.festin.app.waiting.application.port.in.CompleteExperienceUseCase;
import com.festin.app.waiting.application.port.in.result.CompleteResult;
import com.festin.app.waiting.application.port.out.WaitingRepositoryPort;
import com.festin.app.waiting.domain.exception.WaitingNotFoundException;
import com.festin.app.waiting.domain.model.Waiting;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 체험 완료 Service
 *
 * 처리 흐름:
 * 1. DB에서 Waiting 조회
 * 2. boothId 일치 확인
 * 3. 도메인 로직 실행 (waiting.complete() - 상태 ENTERED → COMPLETED)
 * 4. DB 업데이트
 * 5. Redis current -1 (퇴장 처리)
 * 6. 결과 반환
 */
@Service
@Transactional
public class CompleteExperienceService implements CompleteExperienceUseCase {

    private final WaitingRepositoryPort waitingRepositoryPort;
    private final BoothCachePort boothCachePort;

    public CompleteExperienceService(
            WaitingRepositoryPort waitingRepositoryPort,
            BoothCachePort boothCachePort
    ) {
        this.waitingRepositoryPort = waitingRepositoryPort;
        this.boothCachePort = boothCachePort;
    }

    @Override
    public CompleteResult complete(Long boothId, Long waitingId) {
        Waiting waiting = waitingRepositoryPort.findById(waitingId).orElseThrow(WaitingNotFoundException::new);

        if (!waiting.getBoothId().equals(boothId)) {
            throw new WaitingNotFoundException();
        }

        waiting.complete();
        Waiting updated = waitingRepositoryPort.save(waiting);
        boothCachePort.decrementCurrentCount(boothId);

        return CompleteResult.from(updated);
    }
}