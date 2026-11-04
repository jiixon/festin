package com.festin.app.waiting.application.service;

import com.festin.app.booth.application.port.out.BoothCachePort;
import com.festin.app.booth.domain.BoothNotFoundException;
import com.festin.app.booth.domain.model.Booth;
import com.festin.app.booth.domain.model.BoothStatus;
import com.festin.app.waiting.application.event.CallNextEvent;
import com.festin.app.waiting.application.port.in.CallNextUseCase;
import com.festin.app.waiting.application.port.in.result.CallResult;
import com.festin.app.waiting.application.port.out.NotificationPort;
import com.festin.app.waiting.application.port.out.QueueCachePort;
import com.festin.app.waiting.application.port.out.WaitingRepositoryPort;
import com.festin.app.waiting.domain.exception.QueueEmptyException;
import com.festin.app.waiting.domain.model.Waiting;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

/**
 * TransactionalEventListener 방식 CallNext Service (실험용)
 *
 * 흐름:
 * 1. Redis peek (조회만, 제거 안 함)
 * 2. MySQL save (@Transactional)
 * 3. @TransactionalEventListener(AFTER_COMMIT):
 *    - Redis dequeue + removeUserActiveBooth
 *    - 푸시 알림
 *
 * 목적:
 * - TransactionalEventListener 방식의 한계 증명
 * - AFTER_COMMIT Redis 실패 시 불일치 재현
 */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class CallNextServiceV2EventListener implements CallNextUseCase {

    private final BoothCachePort boothCachePort;
    private final QueueCachePort queueCachePort;
    private final WaitingRepositoryPort waitingRepositoryPort;
    private final NotificationPort notificationPort;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public CallResult callNext(Long boothId) {
        // Redis에서 부스 실시간 정보 조회
        BoothStatus status = boothCachePort.getStatus(boothId)
                .orElseThrow(BoothNotFoundException::new);
        Integer capacity = boothCachePort.getCapacity(boothId)
                .orElseThrow(BoothNotFoundException::new);
        String boothName = boothCachePort.getName(boothId)
                .orElseThrow(BoothNotFoundException::new);

        // Booth 도메인 객체 생성
        Booth booth = Booth.of(boothId, boothName, capacity, status);

        // 호출 가능 여부 검증
        int currentCount = boothCachePort.getCurrentCount(boothId);
        booth.validateForCalling(currentCount);

        // ⚠️ Peek: 조회만 하고 제거하지 않음
        // 문제: Peek-Save gap에 Race Condition 가능
        QueueCachePort.QueueItem queueItem = queueCachePort.peekFirst(boothId)
                .orElseThrow(QueueEmptyException::new);

        Long userId = queueItem.userId();
        LocalDateTime registeredAt = queueItem.registeredAt();

        // Waiting 도메인 생성 (CALLED 상태)
        LocalDateTime calledAt = LocalDateTime.now();
        Waiting waiting = Waiting.ofCalled(
                userId,
                boothId,
                1,  // 호출 순번
                registeredAt,
                calledAt
        );

        // MySQL에 저장 (트랜잭션 범위)
        Waiting savedWaiting = waitingRepositoryPort.save(waiting);

        log.info("[TransactionalEventListener] MySQL 저장 완료 - waitingId: {}, userId: {}, boothId: {}",
                savedWaiting.getId(), userId, boothId);

        // 이벤트 발행: AFTER_COMMIT에서 Redis 처리
        eventPublisher.publishEvent(new CallNextEvent(boothId, userId));

        // 결과 반환
        return new CallResult(
                savedWaiting.getId(),
                userId,
                1,
                calledAt
        );
    }

    /**
     * AFTER_COMMIT: MySQL 커밋 후 Redis 작업
     *
     * ⚠️ 이 메서드도 실패 가능!
     * - Redis dequeue 실패
     * - Redis removeUserActiveBooth 실패
     * - 푸시 알림 실패
     *
     * → MySQL은 이미 커밋됨 → 불일치 발생!
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCallNextEvent(CallNextEvent event) {
        log.info("[AFTER_COMMIT] Redis 작업 시작 - boothId: {}, userId: {}", event.boothId(), event.userId());

        try {
            // Redis dequeue: 실제 제거
            queueCachePort.dequeue(event.boothId());

            // 활성 부스 목록에서 제거
            queueCachePort.removeUserActiveBooth(event.userId(), event.boothId());

            log.info("[AFTER_COMMIT] Redis 작업 완료 - boothId: {}, userId: {}", event.boothId(), event.userId());

            // 푸시 알림 발송
            sendNotification(event.boothId(), event.userId());

        } catch (Exception e) {
            // ⚠️ 여기서 예외 발생 시:
            // - MySQL은 이미 커밋됨
            // - Redis는 변경 안 됨
            // - 결과: MySQL에는 있는데 Redis에도 있음 (불일치!)
            log.error("[AFTER_COMMIT] Redis 작업 실패 - boothId: {}, userId: {}", event.boothId(), event.userId(), e);
            // 복구 방법?
            // 1. 재시도? → 트랜잭션 밖이라 롤백 불가
            // 2. 배치 보정? → 결국 필요!
        }
    }

    private void sendNotification(Long boothId, Long userId) {
        try {
            String boothName = boothCachePort.getName(boothId).orElse("부스");
            String eventId = "call:" + boothId + ":" + userId;
            NotificationPort.CallNotification notification = new NotificationPort.CallNotification(
                    eventId,
                    userId,
                    boothId,
                    boothName,
                    1
            );
            notificationPort.send(notification);
        } catch (Exception e) {
            log.error("[AFTER_COMMIT] 푸시 알림 실패 - boothId: {}, userId: {}", boothId, userId, e);
        }
    }
}