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
        // 대기열에 있는지 확인
        if (queueCachePort.getPosition(boothId, userId).isEmpty()) {
            throw WaitingNotFoundException.notInQueue(userId, boothId);
        }

        // Redis 대기열에서 제거
        queueCachePort.remove(boothId, userId);

        // 사용자 활성 부스 목록에서 제거
        queueCachePort.removeUserActiveBooth(userId, boothId);
    }
}
