package com.festin.app.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 인증된 사용자 ID 주입 어노테이션
 *
 * Controller 메서드 파라미터에 사용하여 SecurityContext에서 userId를 자동 주입
 *
 * 사용 예:
 * <pre>
 * {@code
 * @PostMapping("/enqueue")
 * public Response enqueue(@AuthenticatedUserId Long userId, @RequestBody Request request) {
 *     // ...
 * }
 * }
 * </pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthenticatedUserId {
}