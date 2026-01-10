package com.festin.app.user.adapter.in.web.dto;

import com.festin.app.user.application.port.in.dto.LoginCommand;
import com.festin.app.user.domain.model.Role;

public record LoginRequest(
        String email,
        String nickname,
        String role,
        Long managedBoothId  // STAFF 전용 (optional)
) {
    public LoginCommand toCommand() {
        return new LoginCommand(email, nickname, Role.valueOf(role), managedBoothId);
    }
}
