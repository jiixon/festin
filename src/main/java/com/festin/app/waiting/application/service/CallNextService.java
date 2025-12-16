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
 * 해피 케이스 구현:
 * - 부스 정원 검증
 * - Redis 대기열에서 1명 dequeue
 * - MySQL에 Waiting 저장 (CALLED 상태)
 * - 푸시 알림 발송
 * - 사용자 활성 부스 목록에서 제거
 *
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
        Long userId = queueCachePort.dequeue(boothId)
                .orElseThrow(QueueEmptyException::new);

        // 사용자 활성 부스 목록에서 제거 (호출되었으므로 대기 상태 종료)
        queueCachePort.removeUserActiveBooth(userId, boothId);

        // 호출 순번 계산 (dequeue 전 위치)
        int calledPosition = 1; // dequeue되었으므로 1번으로 호출됨

        // 대기 등록 시간 조회 (Redis에서)
        LocalDateTime registeredAt = queueCachePort.getRegisteredAt(boothId, userId)
                .orElse(LocalDateTime.now()); // fallback

        // Waiting 도메인 생성 (CALLED 상태)
        LocalDateTime calledAt = LocalDateTime.now();
        Waiting waiting = Waiting.builder()
                .userId(userId)
                .boothId(boothId)
                .calledPosition(calledPosition)
                .registeredAt(registeredAt)
                .calledAt(calledAt)
                .build();

        // DB에 영구 저장
        Waiting savedWaiting = waitingRepositoryPort.save(waiting);

        // 푸시 알림 발송
        // TODO: 트랜잭셔널 이벤트(@TransactionalEventListener)로 변경하여 DB 커밋 후 실행
        // TODO: RabbitMQ 또는 Kafka로 실제 알림 발송 구현
        NotificationPort.CallNotification notification = new NotificationPort.CallNotification(
                userId,
                boothId,
                booth.getName(),
                calledPosition
        );
        notificationPort.sendCallNotification(notification);

        // 결과 반환
        return new CallResult(
                savedWaiting.getId(),
                userId,
                calledPosition,
                calledAt
        );
    }
}