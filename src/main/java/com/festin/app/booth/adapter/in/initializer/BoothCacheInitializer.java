package com.festin.app.booth.adapter.in.initializer;

import com.festin.app.booth.application.port.out.BoothCachePort;
import com.festin.app.booth.application.port.out.BoothRepositoryPort;
import com.festin.app.booth.application.port.out.dto.BoothInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 서버 시작 시 Redis 부스 캐시 초기화
 *
 * Redis가 primary store이므로 서버 재시작 시
 * DB의 OPEN 부스를 Redis에 미리 적재한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BoothCacheInitializer implements ApplicationRunner {

    private final BoothRepositoryPort boothRepositoryPort;
    private final BoothCachePort boothCachePort;

    @Override
    public void run(ApplicationArguments args) {
        List<BoothInfo> openBooths = boothRepositoryPort.findAllOpenBoothInfo();

        if (openBooths.isEmpty()) {
            log.warn("[BoothCacheInitializer] OPEN 상태 부스 없음 - Redis 초기화 생략");
            return;
        }

        for (BoothInfo info : openBooths) {
            boothCachePort.addBoothId(info.id());
            boothCachePort.setName(info.id(), info.name());
            boothCachePort.setDescription(info.id(), info.description());
            boothCachePort.setUniversityName(info.id(), info.universityName());
            boothCachePort.setStatus(info.id(), info.status());
            boothCachePort.setCapacity(info.id(), info.capacity());
        }

        log.info("[BoothCacheInitializer] Redis 초기화 완료 - {} 개 부스 적재", openBooths.size());
    }
}