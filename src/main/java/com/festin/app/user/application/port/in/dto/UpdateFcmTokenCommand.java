package com.festin.app.user.application.port.in.dto;

/**
 * FCM 토큰 저장 커맨드
 */
public record UpdateFcmTokenCommand(
        Long userId,
        String fcmToken
) {
}