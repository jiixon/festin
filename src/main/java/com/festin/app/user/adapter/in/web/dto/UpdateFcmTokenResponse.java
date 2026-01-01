package com.festin.app.user.adapter.in.web.dto;

/**
 * FCM 토큰 저장 응답 DTO
 */
public record UpdateFcmTokenResponse(
        boolean success
) {
    public static UpdateFcmTokenResponse from() {
        return new UpdateFcmTokenResponse(true);
    }
}