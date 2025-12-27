package com.festin.app.user.adapter.in.web;

import com.festin.app.user.adapter.in.web.dto.LoginRequest;
import com.festin.app.user.adapter.in.web.dto.LoginResponse;
import com.festin.app.user.application.port.in.LoginUseCase;
import com.festin.app.user.application.port.in.dto.LoginResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 Controller
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final LoginUseCase loginUseCase;

    public AuthController(LoginUseCase loginUseCase) {
        this.loginUseCase = loginUseCase;
    }

    /**
     * 간단 로그인 (자동 회원가입)
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        LoginResult result = loginUseCase.login(request.toCommand());
        return ResponseEntity.ok(LoginResponse.from(result));
    }
}