package com.festin.app.user.application.service;

import com.festin.app.user.application.port.in.UpdateFcmTokenUseCase;
import com.festin.app.user.application.port.in.dto.UpdateFcmTokenCommand;
import com.festin.app.user.application.port.out.FcmTokenCachePort;
import com.festin.app.user.application.port.out.UserRepositoryPort;
import com.festin.app.user.domain.UserNotFoundException;
import com.festin.app.user.domain.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FCM 토큰 저장 UseCase 구현
 *
 * 비즈니스 흐름:
 * 1. User 조회 (없으면 예외)
 * 2. MySQL에 FCM 토큰 저장 (영구 저장)
 * 3. Redis에 FCM 토큰 캐싱 (TTL 60일, 푸시 알림 발송용)
 */
@Service
@RequiredArgsConstructor
public class UpdateFcmTokenService implements UpdateFcmTokenUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final FcmTokenCachePort fcmTokenCachePort;

    @Override
    @Transactional
    public void updateFcmToken(UpdateFcmTokenCommand command) {
        Long userId = command.userId();
        String fcmToken = command.fcmToken();

        // User 조회
        User user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> UserNotFoundException.of(userId));

        // 디바이스 등록 (도메인 로직: 알림 활성화 여부 검증, FCM 토큰 검증)
        User updatedUser = user.registerDeviceForNotification(fcmToken);

        // MySQL에 저장 (영구 저장)
        userRepositoryPort.save(updatedUser);

        // Redis에 캐싱 (TTL 60일)
        fcmTokenCachePort.saveFcmToken(userId, fcmToken);
    }
}