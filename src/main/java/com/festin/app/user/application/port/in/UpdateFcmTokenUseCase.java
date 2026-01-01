package com.festin.app.user.application.port.in;

import com.festin.app.user.application.port.in.dto.UpdateFcmTokenCommand;

/**
 * FCM 토큰 저장 Use Case
 *
 * 사용자의 FCM 토큰을 Redis와 MySQL에 저장합니다.
 * - Redis: 푸시 알림 발송용 캐시 (TTL 60일)
 * - MySQL: 영구 저장소 (백업/복구용)
 */
public interface UpdateFcmTokenUseCase {

    /**
     * FCM 토큰 저장
     *
     * @param command FCM 토큰 저장 커맨드
     */
    void updateFcmToken(UpdateFcmTokenCommand command);
}