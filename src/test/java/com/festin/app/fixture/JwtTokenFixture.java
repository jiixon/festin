package com.festin.app.fixture;

import com.festin.app.common.jwt.JwtTokenProvider;
import org.springframework.stereotype.Component;

/**
 * 테스트용 JWT 토큰 생성 Fixture
 *
 * 테스트에서 JWT 토큰을 쉽게 생성할 수 있도록 제공
 */
@Component
public class JwtTokenFixture {

    private final JwtTokenProvider jwtTokenProvider;

    public JwtTokenFixture(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * VISITOR 역할의 JWT 토큰 생성
     */
    public String generateVisitorToken(Long userId, String email) {
        return jwtTokenProvider.generateAccessToken(userId, email, "VISITOR", null);
    }

    /**
     * STAFF 역할의 JWT 토큰 생성
     */
    public String generateStaffToken(Long userId, String email) {
        return jwtTokenProvider.generateAccessToken(userId, email, "STAFF", null);
    }

    /**
     * STAFF 역할의 JWT 토큰 생성 (boothId 포함)
     */
    public String generateStaffToken(Long userId, String email, Long managedBoothId) {
        return jwtTokenProvider.generateAccessToken(userId, email, "STAFF", managedBoothId);
    }

    /**
     * 기본 VISITOR 토큰 (테스트용 이메일 포함)
     */
    public String generateDefaultVisitorToken(Long userId) {
        return generateVisitorToken(userId, "test-" + userId + "@test.com");
    }

    /**
     * 기본 STAFF 토큰 (테스트용 이메일 포함)
     */
    public String generateDefaultStaffToken(Long userId) {
        return generateStaffToken(userId, "staff-" + userId + "@test.com");
    }

    /**
     * 기본 STAFF 토큰 (boothId 포함, 테스트용 이메일 포함)
     */
    public String generateDefaultStaffToken(Long userId, Long managedBoothId) {
        return generateStaffToken(userId, "staff-" + userId + "@test.com", managedBoothId);
    }
}