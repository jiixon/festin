package com.festin.app.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 설정
 *
 * JWT 기반 인증 설정 및 경로별 권한 관리
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@Profile("!test")
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CORS 활성화 (WebConfig의 CORS 설정 사용)
                .cors(Customizer.withDefaults())

                // CSRF 비활성화 (REST API이므로)
                .csrf(AbstractHttpConfigurer::disable)

                // 세션 사용 안 함 (JWT 사용)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // 경로별 권한 설정
                .authorizeHttpRequests(auth -> auth
                        // 인증 불필요 - 로그인
                        .requestMatchers("/api/v1/auth/login").permitAll()

                        // 인증 불필요 - 부스 목록/상세 조회 (GET만)
                        .requestMatchers(HttpMethod.GET, "/api/v1/booths", "/api/v1/booths/*").permitAll()

                        // 인증 불필요 - Health check
                        .requestMatchers("/actuator/**", "/api/health").permitAll()

                        // STAFF 전용 - 부스 관리 API
                        .requestMatchers("/api/v1/booths/*/status").hasRole("STAFF")
                        .requestMatchers("/api/v1/booths/*/called-list").hasRole("STAFF")
                        .requestMatchers("/api/v1/booths/*/call-next").hasRole("STAFF")
                        .requestMatchers("/api/v1/booths/*/call").hasRole("STAFF")
                        .requestMatchers("/api/v1/booths/*/entrance").hasRole("STAFF")
                        .requestMatchers("/api/v1/booths/*/complete").hasRole("STAFF")
                        .requestMatchers("/api/v1/waitings/call").hasRole("STAFF")

                        // 나머지 모든 요청 - 인증 필요
                        .anyRequest().authenticated()
                )

                // JWT 인증 필터 추가
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                // 인증 실패 시 처리
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                );

        return http.build();
    }
}