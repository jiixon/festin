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
import com.festin.app.user.application.port.out.UserRepositoryPort;
import com.festin.app.waiting.domain.model.CallingSession;
import com.festin.app.waiting.domain.model.Waiting;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다음 사람 호출 Service
 *
 * MySQL-Redis 정합성 보장 (Soft Lock 방식):
 * 1. Redis dequeue (즉시 제거, 원자적)
 * 2. 호출 가능한 사용자인지 검증 (좀비 방어)
 * 3. Soft Lock 생성 (temp:calling:{boothId}:{userId}, timestamp 보존)
 * 4. 활성 부스 목록에서 제거
 * 5. MySQL save (@Transactional)
 * 6. 성공 시: Soft Lock 삭제
 * 7. 실패 시: Soft Lock 남김 (배치가 timestamp로 롤백)

 * 참고: 부스 현재 인원(current)은 입장 확인 시점에 +1
 */
@Slf4j
@Service
@Transactional
public class CallNextService implements CallNextUseCase {

    private final BoothCachePort boothCachePort;
    private final QueueCachePort queueCachePort;
    private final WaitingRepositoryPort waitingRepositoryPort;
    private final NotificationPort notificationPort;
    private final UserRepositoryPort userRepositoryPort;

    public CallNextService(
            BoothCachePort boothCachePort,
            QueueCachePort queueCachePort,
            WaitingRepositoryPort waitingRepositoryPort,
            NotificationPort notificationPort,
            UserRepositoryPort userRepositoryPort
    ) {
        this.boothCachePort = boothCachePort;
        this.queueCachePort = queueCachePort;
        this.waitingRepositoryPort = waitingRepositoryPort;
        this.notificationPort = notificationPort;
        this.userRepositoryPort = userRepositoryPort;
    }

    @Override
    public CallResult callNext(Long boothId) {
        Booth booth = boothCachePort.getBooth(boothId).orElseThrow(BoothNotFoundException::new);
        booth.validateForCalling(boothCachePort.getCurrentCount(boothId));

        CallingSession session = dequeueCallableUser(boothId);

        queueCachePort.createSoftLock(session.getBoothId(), session.getUserId(), session.getRegisteredAt());
        queueCachePort.removeUserActiveBooth(session.getUserId(), session.getBoothId());

        Waiting saved = waitingRepositoryPort.save(session.toWaiting());
        queueCachePort.deleteSoftLock(session.getBoothId(), session.getUserId());

        notificationPort.send(session.toNotification(saved.getId(), booth.getName()));
        return session.toResult(saved.getId());
    }

    /**
     * 호출 가능한 사용자를 대기열에서 꺼낸다
     *
     * 대기열은 Redis, 사용자는 MySQL에 있어 둘 사이의 참조 무결성은 보장되지 않는다.
     * 사용자가 사라지면 대기열에 좀비 userId가 남고, 그대로 호출하면
     * Waiting INSERT 단계에서 FK 위반이 발생해 호출이 실패한다.
     * 이때 Soft Lock이 남아 복구 배치가 좀비를 원래 순번으로 되살리므로,
     * 그 부스의 대기열은 같은 좀비에 막혀 영구히 진행되지 못한다.
     *
     * 따라서 Soft Lock을 만들기 전에 사용자 존재를 확인한다.
     * - 좀비는 Soft Lock 없이 폐기 → 복구 배치가 되살리지 않는다
     * - 다음 대기자로 계속 진행 → 좀비가 몇 명이든 부스는 멈추지 않는다
     * - 큐가 완전히 빌 때까지만 반복 → 종료가 보장된다
     */
    private CallingSession dequeueCallableUser(Long boothId) {
        int discarded = 0;

        while (true) {
            QueueCachePort.QueueItem item = queueCachePort.dequeue(boothId)
                    .orElseThrow(QueueEmptyException::new);

            if (userRepositoryPort.existsById(item.userId())) {
                if (discarded > 0) {
                    log.warn("[좀비 대기자] 폐기 후 호출 진행 - boothId: {}, 폐기: {}건, 호출: {}",
                            boothId, discarded, item.userId());
                }
                return CallingSession.from(boothId, item);
            }

            // 좀비: 활성 부스 목록까지 정리하고 다음 대기자로 진행
            queueCachePort.removeUserActiveBooth(item.userId(), boothId);
            discarded++;
            log.warn("[좀비 대기자] 존재하지 않는 사용자 폐기 - boothId: {}, userId: {}", boothId, item.userId());
        }
    }
}