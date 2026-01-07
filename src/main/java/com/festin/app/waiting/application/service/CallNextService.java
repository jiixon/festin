package com.festin.app.waiting.application.service;

import com.festin.app.booth.application.port.out.BoothCachePort;
import com.festin.app.booth.domain.BoothNotFoundException;
import com.festin.app.booth.domain.model.Booth;
import com.festin.app.waiting.application.port.in.CallNextUseCase;
import com.festin.app.waiting.application.port.in.result.CallResult;
import com.festin.app.waiting.application.port.out.NotificationPort;
import com.festin.app.waiting.application.port.out.QueueCachePort;
import com.festin.app.waiting.application.port.out.WaitingRepositoryPort;
import com.festin.app.waiting.domain.exception.QueueEmptyException;
import com.festin.app.waiting.domain.model.CallingSession;
import com.festin.app.waiting.domain.model.Waiting;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다음 사람 호출 Service
 *
 * MySQL-Redis 정합성 보장 (Soft Lock 방식):
 * 1. Redis dequeue (즉시 제거, 원자적)
 * 2. Soft Lock 생성 (temp:calling:{boothId}:{userId}, timestamp 보존)
 * 3. 활성 부스 목록에서 제거
 * 4. MySQL save (@Transactional)
 * 5. 성공 시: Soft Lock 삭제
 * 6. 실패 시: Soft Lock 남김 (배치가 timestamp로 롤백)

 * 참고: 부스 현재 인원(current)은 입장 확인 시점에 +1
 */
@Service
@Transactional
public class CallNextService implements CallNextUseCase {

    private final BoothCachePort boothCachePort;
    private final QueueCachePort queueCachePort;
    private final WaitingRepositoryPort waitingRepositoryPort;
    private final NotificationPort notificationPort;

    public CallNextService(
            BoothCachePort boothCachePort,
            QueueCachePort queueCachePort,
            WaitingRepositoryPort waitingRepositoryPort,
            NotificationPort notificationPort
    ) {
        this.boothCachePort = boothCachePort;
        this.queueCachePort = queueCachePort;
        this.waitingRepositoryPort = waitingRepositoryPort;
        this.notificationPort = notificationPort;
    }

    @Override
    public CallResult callNext(Long boothId) {
        Booth booth = boothCachePort.getBooth(boothId).orElseThrow(BoothNotFoundException::new);
        booth.validateForCalling(boothCachePort.getCurrentCount(boothId));

        CallingSession session = CallingSession.from(boothId, queueCachePort.dequeue(boothId).orElseThrow(QueueEmptyException::new));

        queueCachePort.createSoftLock(session.getBoothId(), session.getUserId(), session.getRegisteredAt());
        queueCachePort.removeUserActiveBooth(session.getUserId(), session.getBoothId());

        Waiting saved = waitingRepositoryPort.save(session.toWaiting());
        queueCachePort.deleteSoftLock(session.getBoothId(), session.getUserId());

        notificationPort.send(session.toNotification(saved.getId(), booth.getName()));
        return session.toResult(saved.getId());
    }
}