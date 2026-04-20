package com.festin.app.booth.application.service;

import com.festin.app.booth.application.port.in.GetBoothListUseCase;
import com.festin.app.booth.application.port.in.dto.BoothListResult;
import com.festin.app.booth.application.port.out.BoothCachePort;
import com.festin.app.booth.application.port.out.BoothCachePort.BoothMeta;
import com.festin.app.booth.application.port.out.BoothRepositoryPort;
import com.festin.app.booth.application.port.out.dto.BoothInfo;
import com.festin.app.waiting.application.port.out.QueueCachePort;
import com.festin.app.waiting.domain.model.EstimatedWaitTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 부스 목록 조회 Service
 *
 * 성능 최적화:
 * - Redis Pipeline으로 부스 메타정보 + 대기인원 일괄 조회
 * - DB 접근 최소화 (캐시 미스 시에만 DB 조회)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GetBoothListService implements GetBoothListUseCase {

    private final BoothRepositoryPort boothRepositoryPort;
    private final BoothCachePort boothCachePort;
    private final QueueCachePort queueCachePort;

    @Override
    public BoothListResult getBoothList(Long universityId) {
        // universityId 필터링이 있으면 DB 조회 (Redis에 universityId 없음)
        if (universityId != null) {
            return getBoothListFromDb(universityId);
        }

        // 전체 조회: Redis 우선 사용
        return getBoothListFromRedis();
    }

    /**
     * Redis에서 부스 목록 조회 (성능 최적화)
     */
    private BoothListResult getBoothListFromRedis() {
        // 1. Redis에서 부스 ID 목록 조회
        List<Long> boothIds = boothCachePort.getAllBoothIds();

        // 캐시 미스: DB에서 조회 후 캐시 워밍
        if (boothIds.isEmpty()) {
            log.info("부스 캐시 미스 - DB에서 조회 후 캐시 워밍");
            return getBoothListFromDbAndWarmCache();
        }

        // 2. Pipeline으로 부스 메타정보 + 대기인원 일괄 조회
        Map<Long, BoothMeta> boothMetas = boothCachePort.getBoothMetas(boothIds);
        Map<Long, Integer> waitingCounts = queueCachePort.getQueueSizes(boothIds);

        // 3. 결과 조합
        List<BoothListResult.BoothItem> boothItems = new ArrayList<>();
        for (Long boothId : boothIds) {
            BoothMeta meta = boothMetas.get(boothId);
            if (meta == null || meta.status() == null) {
                continue;  // 불완전한 캐시 데이터 스킵
            }

            int currentWaiting = waitingCounts.getOrDefault(boothId, 0);
            EstimatedWaitTime estimatedWaitTime = EstimatedWaitTime.fromWaitingCount(
                    currentWaiting, meta.capacity());

            boothItems.add(new BoothListResult.BoothItem(
                    boothId,
                    meta.name(),
                    meta.description(),
                    meta.universityName(),
                    meta.status(),
                    meta.capacity(),
                    currentWaiting,
                    estimatedWaitTime.minutes()
            ));
        }

        return new BoothListResult(boothItems);
    }

    /**
     * DB에서 부스 목록 조회 (universityId 필터링)
     */
    private BoothListResult getBoothListFromDb(Long universityId) {
        List<BoothInfo> boothInfoList = boothRepositoryPort.findAllBoothInfoByUniversityId(universityId);

        List<Long> boothIds = boothInfoList.stream().map(BoothInfo::id).toList();
        Map<Long, Integer> waitingCounts = queueCachePort.getQueueSizes(boothIds);

        List<BoothListResult.BoothItem> boothItems = boothInfoList.stream()
                .map(info -> toBoothItem(info, waitingCounts.getOrDefault(info.id(), 0)))
                .toList();

        return new BoothListResult(boothItems);
    }

    /**
     * DB에서 부스 목록 조회 후 캐시 워밍
     */
    private BoothListResult getBoothListFromDbAndWarmCache() {
        List<BoothInfo> boothInfoList = boothRepositoryPort.findAllOpenBoothInfo();

        // 캐시 워밍
        for (BoothInfo info : boothInfoList) {
            boothCachePort.addBoothId(info.id());
            boothCachePort.setName(info.id(), info.name());
            boothCachePort.setDescription(info.id(), info.description());
            boothCachePort.setUniversityName(info.id(), info.universityName());
            boothCachePort.setStatus(info.id(), info.status());
            boothCachePort.setCapacity(info.id(), info.capacity());
        }
        log.info("부스 캐시 워밍 완료 - {} 개 부스", boothInfoList.size());

        List<Long> boothIds = boothInfoList.stream().map(BoothInfo::id).toList();
        Map<Long, Integer> waitingCounts = queueCachePort.getQueueSizes(boothIds);

        List<BoothListResult.BoothItem> boothItems = boothInfoList.stream()
                .map(info -> toBoothItem(info, waitingCounts.getOrDefault(info.id(), 0)))
                .toList();

        return new BoothListResult(boothItems);
    }

    private BoothListResult.BoothItem toBoothItem(BoothInfo info, int currentWaiting) {
        EstimatedWaitTime estimatedWaitTime = EstimatedWaitTime.fromWaitingCount(
                currentWaiting, info.capacity());

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