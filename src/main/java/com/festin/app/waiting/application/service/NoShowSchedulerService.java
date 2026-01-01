package com.festin.app.waiting.application.service;

import com.festin.app.waiting.application.port.out.WaitingRepositoryPort;
import com.festin.app.waiting.domain.model.Waiting;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 노쇼 자동 처리 스케줄러
 *
 * 비즈니스 규칙:
 * - 호출 후 5분 경과 시 자동 노쇼 처리
 * - 1분마다 실행
 * - CALLED 상태인 대기 건 중 타임아웃된 건 조회 및 노쇼 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoShowSchedulerService {

    private final WaitingRepositoryPort waitingRepositoryPort;

    private static final int TIMEOUT_MINUTES = 5;

    /**
     * 노쇼 자동 처리 (1분마다 실행)
     *
     * 실행 주기: 1분 (60,000ms)
     * 타임아웃 기준: 호출 시각 기준 5분 경과
     *
     * 처리 흐름:
     * 1. 타임아웃된 대기 건 조회 (CALLED 상태 && calledAt < 현재시각 - 5분)
     * 2. 각 대기 건에 대해 노쇼 처리 (markAsNoShow())
     * 3. MySQL에 저장 (상태: COMPLETED, 완료유형: NO_SHOW)
     */
    @Scheduled(fixedDelay = 60000) // 1분마다 실행
    @Transactional
    public void processNoShow() {
        LocalDateTime timeoutThreshold = LocalDateTime.now().minusMinutes(TIMEOUT_MINUTES);

        // 타임아웃된 대기 건 조회
        List<Waiting> timeoutWaitings = waitingRepositoryPort.findTimeoutWaitings(timeoutThreshold);

        if (timeoutWaitings.isEmpty()) {
            log.debug("노쇼 처리 대상 없음");
            return;
        }

        log.info("노쇼 처리 시작: {} 건", timeoutWaitings.size());

        // 각 대기 건에 대해 노쇼 처리
        for (Waiting waiting : timeoutWaitings) {
            try {
                waiting.markAsNoShow();
                waitingRepositoryPort.save(waiting);

                log.info("노쇼 처리 완료 - waitingId: {}, userId: {}, boothId: {}, calledAt: {}",
                        waiting.getId(),
                        waiting.getUserId(),
                        waiting.getBoothId(),
                        waiting.getCalledAt());

            } catch (Exception e) {
                log.error("노쇼 처리 실패 - waitingId: {}, error: {}",
                        waiting.getId(), e.getMessage(), e);
            }
        }

        log.info("노쇼 처리 완료: {} 건", timeoutWaitings.size());
    }
}