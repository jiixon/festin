package com.festin.app.user.adapter.in.web;

import com.festin.app.common.security.AuthenticatedUserId;
import com.festin.app.user.adapter.in.web.dto.UpdateFcmTokenRequest;
import com.festin.app.user.adapter.in.web.dto.UpdateFcmTokenResponse;
import com.festin.app.user.application.port.in.UpdateFcmTokenUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 사용자 Controller
 *
 * API 스펙:
 * - POST /api/v1/users/fcm-token - FCM 토큰 저장
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UpdateFcmTokenUseCase updateFcmTokenUseCase;

    /**
     * FCM 토큰 저장
     *
     * POST /api/v1/users/fcm-token
     *
     * @param userId 사용자 ID (임시로 헤더로 받음, 추후 JWT 인증으로 대체)
     * @param request FCM 토큰 저장 요청
     * @return 200 OK - 성공 여부
     */
    @PostMapping("/fcm-token")
    public ResponseEntity<UpdateFcmTokenResponse> updateFcmToken(
            @AuthenticatedUserId Long userId,
            @RequestBody UpdateFcmTokenRequest request
    ) {
        updateFcmTokenUseCase.updateFcmToken(request.toCommand(userId));
        return ResponseEntity.ok(UpdateFcmTokenResponse.from());
    }
}