package com.festin.app.common.security;

import tools.jackson.databind.ObjectMapper;
import com.festin.app.common.dto.ErrorResponse;
import com.festin.app.common.exception.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * JWT 인증 실패 처리
 *
 * 인증되지 않은 사용자의 접근 시 401 Unauthorized 응답 반환
 * GlobalExceptionHandler의 ErrorResponse 형식과 일관성 유지
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {

        // UNAUTHORIZED 에러 응답
        ErrorResponse errorResponse = ErrorResponse.of(ErrorCode.UNAUTHORIZED);

        // 401 Unauthorized 응답
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}