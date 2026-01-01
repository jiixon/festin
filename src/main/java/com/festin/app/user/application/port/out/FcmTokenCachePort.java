package com.festin.app.user.application.port.out;

import java.util.Optional;

/**
 * FCM 토큰 캐시 Port
 *
 * Redis에 FCM 토큰을 캐싱합니다.
 * - 키: fcm:token:{userId}
 * - TTL: 60일 (5184000초)
 */
public interface FcmTokenCachePort {

    /**
     * FCM 토큰 저장 (TTL 60일)
     *
     * @param userId 사용자 ID
     * @param fcmToken FCM 토큰
     */
    void saveFcmToken(Long userId, String fcmToken);

    /**
     * FCM 토큰 조회
     *
     * @param userId 사용자 ID
     * @return FCM 토큰 (Optional)
     */
    Optional<String> getFcmToken(Long userId);

    /**
     * FCM 토큰 삭제
     *
     * @param userId 사용자 ID
     */
    void deleteFcmToken(Long userId);
}