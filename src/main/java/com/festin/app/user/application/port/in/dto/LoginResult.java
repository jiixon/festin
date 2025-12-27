package com.festin.app.user.application.port.in.dto;

import com.festin.app.user.domain.model.Role;

public record LoginResult(
        String accessToken,
        Long userId,
        String email,
        String nickname,
        Role role
) {
}
