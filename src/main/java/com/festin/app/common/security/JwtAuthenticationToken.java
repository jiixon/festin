package com.festin.app.common.security;

import com.festin.app.user.domain.model.Role;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;

/**
 * JWT 인증 토큰
 *
 * Spring Security의 Authentication 구현체
 * 인증된 사용자의 userId와 role을 저장
 */
public class JwtAuthenticationToken extends AbstractAuthenticationToken {

    private final Long userId;
    private final String email;
    private final Role role;

    public JwtAuthenticationToken(Long userId, String email, Role role) {
        super(convertRoleToAuthorities(role));
        this.userId = userId;
        this.email = email;
        this.role = role;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return userId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }

    /**
     * Role을 Spring Security의 GrantedAuthority로 변환
     *
     * STAFF -> ROLE_STAFF
     * VISITOR -> ROLE_VISITOR
     */
    private static Collection<? extends GrantedAuthority> convertRoleToAuthorities(Role role) {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
}