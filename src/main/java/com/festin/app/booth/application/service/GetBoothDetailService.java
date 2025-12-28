package com.festin.app.booth.application.service;

import com.festin.app.booth.application.port.in.GetBoothDetailUseCase;
import com.festin.app.booth.application.port.in.dto.BoothDetailResult;
import com.festin.app.booth.application.port.out.BoothCachePort;
import com.festin.app.booth.application.port.out.BoothRepositoryPort;
import com.festin.app.booth.application.port.out.dto.BoothInfo;
import com.festin.app.booth.domain.BoothNotFoundException;
import com.festin.app.waiting.application.port.out.QueueCachePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 부스 상세 조회 Service
 *
 * 책임:
 * - BoothInfo + Redis 데이터 조합
 * - 예상 대기 시간 계산
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetBoothDetailService implements GetBoothDetailUseCase {

    private static final int AVERAGE_EXPERIENCE_TIME_MINUTES = 10;

    private final BoothRepositoryPort boothRepositoryPort;
    private final BoothCachePort boothCachePort;
    private final QueueCachePort queueCachePort;

    @Override
    public BoothDetailResult getBoothDetail(Long boothId) {
        // 1. BoothInfo 조회
        BoothInfo boothInfo = boothRepositoryPort.findBoothInfoById(boothId)
            .orElseThrow(BoothNotFoundException::new);

        // 2. Redis에서 현재 입장 인원 조회
        int currentPeople = boothCachePort.getCurrentCount(boothId);

        // 3. Redis에서 대기 인원 조회
        int totalWaiting = queueCachePort.getQueueSize(boothId);

        // 4. 예상 대기 시간 계산
        int estimatedWaitTime = calculateEstimatedWaitTime(totalWaiting, boothInfo.capacity());

        return new BoothDetailResult(
            boothInfo.id(),
            boothInfo.name(),
            boothInfo.description(),
            boothInfo.universityName(),
            boothInfo.status(),
            boothInfo.capacity(),
            currentPeople,
            totalWaiting,
            estimatedWaitTime,
            boothInfo.openTime(),
            boothInfo.closeTime()
        );
    }

    /**
     * 예상 대기 시간 계산
     *
     * @param waitingCount 대기 인원
     * @param capacity     수용 인원
     * @return 예상 대기 시간 (분)
     */
    private int calculateEstimatedWaitTime(int waitingCount, int capacity) {
        if (waitingCount == 0 || capacity == 0) {
            return 0;
        }

        // 필요한 라운드 수 계산 (올림)
        int rounds = (waitingCount + capacity - 1) / capacity;

        // 각 라운드당 평균 체험 시간 적용
        return rounds * AVERAGE_EXPERIENCE_TIME_MINUTES;
    }
}