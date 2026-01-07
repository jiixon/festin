package com.festin.app.user.application.port.out;

/**
 * 토큰 생성 Port
 *
 * 책임:
 * - 사용자 인증 정보로부터 액세스 토큰 생성
 * - 기술 구현(JWT, OAuth 등)으로부터 Application Layer 격리
 *
 * 구현체:
 * - JwtTokenProvider (common/jwt)
 */
public interface TokenGeneratorPort {

    /**
     * 액세스 토큰 생성
     *
     * @param userId 사용자 ID
     * @param email 이메일
     * @param role 역할
     * @return 액세스 토큰
     */
    String generateAccessToken(Long userId, String email, String role);
}