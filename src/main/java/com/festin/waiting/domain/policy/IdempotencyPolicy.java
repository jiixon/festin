package com.festin.app.domain.policy;

import com.festin.app.application.port.out.IdempotencyCachePort;
import com.festin.app.domain.exception.AlreadyRegisteredException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 멱등성 정책
 *
 * 비즈니스 규칙:
 * - 당일 내 같은 부스 재등록 불가
 * - 멱등성 키로 중복 요청 방지 (TTL 24시간)
 *
 * 멱등성 키 형식:
 * - "idempotency:userId:{userId}:boothId:{boothId}:date:{yyyyMMdd}"
 */
@Component
public class IdempotencyPolicy {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 멱등성 키 생성
     *
     * @param userId 사용자 ID
     * @param boothId 부스 ID
     * @return 멱등성 키
     */
    public String generateKey(Long userId, Long boothId) {
        String today = LocalDate.now().format(DATE_FORMATTER);
        return String.format("idempotency:userId:%d:boothId:%d:date:%s", userId, boothId, today);
    }

    /**
     * 멱등성 검증
     *
     * @param key 멱등성 키
     * @param cachePort 캐시 포트
     * @throws AlreadyRegisteredException 이미 등록된 경우 (재등록 시도)
     */
    public void validate(String key, IdempotencyCachePort cachePort) {
        if (cachePort.exists(key)) {
            throw new AlreadyRegisteredException();
        }
    }
}