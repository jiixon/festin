package com.festin.app.user.application.service;

import com.festin.app.user.application.port.in.LoginUseCase;
import com.festin.app.user.application.port.in.dto.LoginCommand;
import com.festin.app.user.application.port.in.dto.LoginResult;
import com.festin.app.user.application.port.out.TokenGeneratorPort;
import com.festin.app.user.application.port.out.UserRepositoryPort;
import com.festin.app.user.domain.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 Service
 *
 * 책임:
 * - 이메일 기반 자동 회원가입 또는 로그인 (Upsert)
 * - 토큰 생성은 Port를 통해 위임
 */
@Service
@RequiredArgsConstructor
public class LoginService implements LoginUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final TokenGeneratorPort tokenGeneratorPort;

    @Override
    @Transactional
    public LoginResult login(LoginCommand command) {
        // Email로 사용자 조회 (Upsert)
        User user = userRepositoryPort.findByEmail(command.email())
                .map(existingUser -> {
                    // 기존 사용자: 닉네임 업데이트
                    return User.of(
                            existingUser.getId(),
                            existingUser.getEmail(),
                            command.nickname(),  // 닉네임 업데이트
                            existingUser.getRole(),
                            existingUser.getFcmToken(),
                            existingUser.getNotificationEnabled()
                    );
                })
                .orElseGet(() -> {
                    // 신규 사용자: 자동 회원가입
                    return User.of(null, command.email(), command.nickname(), command.role());
                });

        // 저장
        User savedUser = userRepositoryPort.save(user);

        // 액세스 토큰 생성 (Port를 통해 위임)
        String accessToken = tokenGeneratorPort.generateAccessToken(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getRole().name()
        );

        return new LoginResult(
                accessToken,
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getNickname(),
                savedUser.getRole()
        );
    }
}
