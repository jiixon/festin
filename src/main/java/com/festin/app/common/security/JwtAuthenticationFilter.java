package com.festin.app.common.security;

import com.festin.app.common.jwt.JwtTokenProvider;
import com.festin.app.user.domain.model.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 인증 필터
 *
 * Authorization 헤더에서 JWT 토큰을 추출하고 검증합니다.
 * 검증된 사용자 정보를 SecurityContext에 저장합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            // Authorization 헤더에서 JWT 토큰 추출
            String token = extractToken(request);

            if (token != null) {
                // JWT 토큰 검증 및 사용자 정보 추출
                Long userId = jwtTokenProvider.getUserIdFromToken(token);
                String email = jwtTokenProvider.getEmailFromToken(token);
                String roleString = jwtTokenProvider.getRoleFromToken(token);
                Role role = Role.valueOf(roleString);

                // JwtAuthenticationToken 생성 및 SecurityContext에 저장
                JwtAuthenticationToken authentication = new JwtAuthenticationToken(userId, email, role);
                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("JWT 인증 성공 - userId: {}, email: {}, role: {}, path: {}",
                        userId, email, role, request.getRequestURI());
            }

        } catch (Exception e) {
            log.warn("JWT 인증 실패 - path: {}, error: {}", request.getRequestURI(), e.getMessage());
            // 인증 실패 시 SecurityContext를 비워두면 Spring Security가 401 처리
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Authorization 헤더에서 Bearer 토큰 추출
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }

        return null;
    }
}