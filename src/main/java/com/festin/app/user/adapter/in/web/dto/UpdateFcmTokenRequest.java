package com.festin.app.user.adapter.in.web.dto;

import com.festin.app.user.application.port.in.dto.UpdateFcmTokenCommand;

/**
 * FCM 토큰 저장 요청 DTO
 */
public record UpdateFcmTokenRequest(
        String fcmToken
) {
    public UpdateFcmTokenCommand toCommand(Long userId) {
        return new UpdateFcmTokenCommand(userId, fcmToken);
    }
}