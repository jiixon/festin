package com.festin.app.waiting.application.service;

import com.festin.app.booth.application.port.out.BoothCachePort;
import com.festin.app.booth.domain.BoothNotFoundException;
import com.festin.app.booth.domain.model.Booth;
import com.festin.app.booth.domain.model.BoothStatus;
import com.festin.app.waiting.application.port.in.CallNextUseCase;
import com.festin.app.waiting.application.port.in.result.CallResult;
import com.festin.app.waiting.application.port.out.NotificationPort;
import com.festin.app.waiting.application.port.out.QueueCachePort;
import com.festin.app.waiting.application.port.out.WaitingRepositoryPort;
import com.festin.app.waiting.domain.exception.QueueEmptyException;
import com.festin.app.waiting.domain.model.Waiting;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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

        // 호출 가능 여부 검증 (Booth 도메인 로직)
        int currentCount = boothCachePort.getCurrentCount(boothId);
        booth.validateForCalling(currentCount);

        // Redis 대기열에서 다음 사용자 dequeue
        QueueCachePort.QueueItem queueItem = queueCachePort.dequeue(boothId)
                .orElseThrow(QueueEmptyException::new);

        Long userId = queueItem.userId();
        LocalDateTime registeredAt = queueItem.registeredAt();

        // Soft Lock 생성
        queueCachePort.createSoftLock(boothId, userId, registeredAt);

        try {
            // 사용자 활성 부스 목록에서 제거
            queueCachePort.removeUserActiveBooth(userId, boothId);

            // 호출 순번 계산
            int calledPosition = 1;

            // Waiting 도메인 생성 (CALLED 상태)
            LocalDateTime calledAt = LocalDateTime.now();
            Waiting waiting = Waiting.ofCalled(
                    userId,
                    boothId,
                    calledPosition,
                    registeredAt,
                    calledAt
            );

            // DB에 영구 저장
            Waiting savedWaiting = waitingRepositoryPort.save(waiting);

            // 성공: Soft Lock 삭제
            queueCachePort.deleteSoftLock(boothId, userId);

            // 푸시 알림 발송
            String eventId = "call:" + savedWaiting.getId();
            NotificationPort.CallNotification notification = new NotificationPort.CallNotification(
                    eventId,
                    userId,
                    boothId,
                    booth.getName(),
                    calledPosition
            );
            notificationPort.send(notification);

            // 결과 반환
            return new CallResult(
                    savedWaiting.getId(),
                    userId,
                    calledPosition,
                    calledAt
            );

        } catch (Exception e) {
            // MySQL 실패: Soft Lock 남김 (배치가 롤백)
            // Soft Lock은 삭제하지 않음 → SoftLockRecoveryBatch가 감지하여 Redis 롤백
            throw e;
        }
    }
}