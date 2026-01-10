package com.festin.app.user.adapter.in.web.dto;

import com.festin.app.user.application.port.in.dto.LoginResult;

public record LoginResponse(
        String accessToken,
        Long userId,
        String email,
        String nickname,
        String role,
        Long managedBoothId
) {
    public static LoginResponse from(LoginResult result) {
        return new LoginResponse(
                result.accessToken(),
                result.userId(),
                result.email(),
                result.nickname(),
                result.role().name(),
                result.managedBoothId()
        );
    }
}
