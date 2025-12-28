package com.festin.app.booth.application.service;

import com.festin.app.booth.application.port.in.GetBoothListUseCase;
import com.festin.app.booth.application.port.in.dto.BoothListResult;
import com.festin.app.booth.application.port.out.BoothRepositoryPort;
import com.festin.app.booth.application.port.out.dto.BoothInfo;
import com.festin.app.waiting.application.port.out.QueueCachePort;
import com.festin.app.waiting.domain.model.EstimatedWaitTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 부스 목록 조회 Service
 */
@Service
@RequiredArgsConstructor
public class GetBoothListService implements GetBoothListUseCase {

    private final BoothRepositoryPort boothRepositoryPort;
    private final QueueCachePort queueCachePort;

    @Override
    public BoothListResult getBoothList(Long universityId) {
        // 1. BoothInfo 목록 조회
        List<BoothInfo> boothInfoList = (universityId == null)
                ? boothRepositoryPort.findAllBoothInfo()
                : boothRepositoryPort.findAllBoothInfoByUniversityId(universityId);

        // 2. 각 부스별 대기 인원 및 예상 시간 조회
        List<BoothListResult.BoothItem> boothItems = boothInfoList.stream()
                .map(this::toBoothItem)
                .collect(Collectors.toList());

        return new BoothListResult(boothItems);
    }

    private BoothListResult.BoothItem toBoothItem(BoothInfo info) {
        // Redis에서 현재 대기 인원 조회
        int currentWaiting = queueCachePort.getQueueSize(info.id());

        // 예상 대기 시간 계산 (Domain Value Object 사용)
        EstimatedWaitTime estimatedWaitTime = EstimatedWaitTime.fromWaitingCount(currentWaiting, info.capacity());

        return new BoothListResult.BoothItem(
                info.id(),
                info.name(),
                info.description(),
                info.universityName(),
                info.status(),
                info.capacity(),
                currentWaiting,
                estimatedWaitTime.minutes()
        );
    }
}