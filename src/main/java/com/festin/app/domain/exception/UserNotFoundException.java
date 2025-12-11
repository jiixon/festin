package com.festin.app.domain.exception;

/**
 * 사용자를 찾을 수 없는 경우 발생하는 예외
 *
 * 발생 상황:
 * - 존재하지 않는 사용자 ID로 조회 시도
 * - 탈퇴한 사용자 접근 시도
 */
public class UserNotFoundException extends RuntimeException {

    private UserNotFoundException(Long userId) {
        super("사용자를 찾을 수 없습니다. (ID: " + userId + ")");
    }

    public static UserNotFoundException of(Long userId) {
        return new UserNotFoundException(userId);
    }
}