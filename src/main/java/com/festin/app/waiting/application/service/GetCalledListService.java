package com.festin.app.waiting.application.service;

import com.festin.app.waiting.application.port.in.GetCalledListUseCase;
import com.festin.app.waiting.application.port.in.result.CalledListResult;
import com.festin.app.waiting.application.port.in.result.CalledListResult.CalledItem;
import com.festin.app.waiting.application.port.out.WaitingRepositoryPort;
import com.festin.app.waiting.application.port.out.dto.CalledWaitingInfo;
import com.festin.app.waiting.domain.policy.NoShowPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 호출 대기 목록 조회 Service
 *
 * 책임:
 * - 부스의 호출된 대기 목록 조회
 * - NoShowPolicy를 통한 남은 시간 계산
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetCalledListService implements GetCalledListUseCase {

    private final WaitingRepositoryPort waitingRepositoryPort;
    private final NoShowPolicy noShowPolicy;

    /**
     * 부스의 호출된 대기 목록 조회
     *
     * 1. 호출된 대기 목록 조회 (User 정보 포함)
     * 2. NoShowPolicy를 통한 남은 시간 계산
     * 3. Result 변환
     *
     * @param boothId 부스 ID
     * @return 호출된 대기 목록 (남은 시간 포함)
     */
    @Override
    public CalledListResult getCalledList(Long boothId) {
        // 1. 호출된 대기 목록 조회 (User 정보 포함)
        List<CalledWaitingInfo> calledWaitings =
            waitingRepositoryPort.findCalledByBoothIdWithUserInfo(boothId);

        // 2. NoShowPolicy를 통한 남은 시간 계산 및 Result 변환
        LocalDateTime now = LocalDateTime.now();
        List<CalledItem> items = calledWaitings.stream()
            .map(info -> {
                // Domain Policy를 통한 남은 시간 계산
                int remainingSeconds = noShowPolicy.calculateRemainingTime(info.calledAt(), now);

                return new CalledItem(
                    info.waitingId(),
                    info.userId(),
                    info.nickname(),
                    info.position(),
                    info.status(),
                    info.calledAt(),
                    remainingSeconds
                );
            })
            .toList();

        return new CalledListResult(items);
    }
}