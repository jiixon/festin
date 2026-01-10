package com.festin.app.common.jwt;

import com.festin.app.user.application.port.out.TokenGeneratorPort;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 토큰 생성 및 검증 구현체
 *
 * TokenGeneratorPort 구현:
 * - Application Layer는 Port를 통해서만 접근
 * - JWT 구현 세부사항은 Infrastructure Layer에 격리
 */
@Component
public class JwtTokenProvider implements TokenGeneratorPort {

    private final SecretKey secretKey;
    private final long expiration;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
        this.expiration = jwtProperties.getExpiration();
    }

    /**
     * JWT 액세스 토큰 생성 (Port 구현)
     */
    @Override
    public String generateAccessToken(Long userId, String email, String role, Long managedBoothId) {
        return generateToken(userId, email, role, managedBoothId);
    }

    /**
     * JWT 토큰 생성 (내부 구현)
     */
    private String generateToken(Long userId, String email, String role, Long managedBoothId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        var builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiryDate);

        // STAFF인 경우 boothId 추가
        if (managedBoothId != null) {
            builder.claim("boothId", managedBoothId);
        }

        return builder.signWith(secretKey).compact();
    }

    /**
     * JWT 토큰 검증 및 Claims 추출
     */
    public Claims validateAndGetClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 토큰에서 userId 추출
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = validateAndGetClaims(token);
        return Long.parseLong(claims.getSubject());
    }

    /**
     * 토큰에서 email 추출
     */
    public String getEmailFromToken(String token) {
        Claims claims = validateAndGetClaims(token);
        return claims.get("email", String.class);
    }

    /**
     * 토큰에서 role 추출
     */
    public String getRoleFromToken(String token) {
        Claims claims = validateAndGetClaims(token);
        return claims.get("role", String.class);
    }

    /**
     * 토큰에서 boothId 추출 (STAFF 전용)
     */
    public Long getBoothIdFromToken(String token) {
        Claims claims = validateAndGetClaims(token);
        Integer boothId = claims.get("boothId", Integer.class);
        return boothId != null ? boothId.longValue() : null;
    }
}
