package com.festin.app.waiting.application.service;

import com.festin.app.waiting.application.port.in.CancelWaitingUseCase;
import com.festin.app.waiting.application.port.out.QueueCachePort;
import com.festin.app.waiting.domain.exception.WaitingNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대기 취소 Service
 *
 * 해피 케이스 구현:
 * - Redis 대기열에서 제거
 */
@Service
@Transactional
public class CancelWaitingService implements CancelWaitingUseCase {

    private final QueueCachePort queueCachePort;

    public CancelWaitingService(QueueCachePort queueCachePort) {
        this.queueCachePort = queueCachePort;
    }

    @Override
    public void cancel(Long userId, Long boothId) {
        if (queueCachePort.getPosition(boothId, userId).isEmpty()) {
            throw WaitingNotFoundException.notInQueue(userId, boothId);
        }

        queueCachePort.remove(boothId, userId);
        queueCachePort.removeUserActiveBooth(userId, boothId);
    }
}
