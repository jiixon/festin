package com.festin.app.user.application.port.in;

import com.festin.app.user.application.port.in.dto.LoginCommand;
import com.festin.app.user.application.port.in.dto.LoginResult;

/**
 * 로그인 UseCase
 *
 * 책임:
 * - Email 기반 자동 회원가입/로그인 (Upsert)
 * - JWT 토큰 발급
 */
public interface LoginUseCase {

    /**
     * 로그인 (Upsert)
     * - Email이 없으면 자동 회원가입
     * - Email이 있으면 닉네임 업데이트 후 로그인
     */
    LoginResult login(LoginCommand command);
}