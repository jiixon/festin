package com.festin.app.user.domain;

import com.festin.app.common.exception.DomainException;
import com.festin.app.common.exception.ErrorCode;

/**
 * 사용자를 찾을 수 없는 경우 발생하는 예외
 *
 * 발생 상황:
 * - 존재하지 않는 사용자 ID로 조회 시도
 * - 탈퇴한 사용자 접근 시도
 */
public class UserNotFoundException extends DomainException {

    private UserNotFoundException(Long userId) {
        super(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다. (ID: " + userId + ")");
    }

    public static UserNotFoundException of(Long userId) {
        return new UserNotFoundException(userId);
    }
}