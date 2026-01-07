package com.festin.app.user.domain;

import com.festin.app.common.exception.DomainException;
import com.festin.app.common.exception.ErrorCode;

/**
 * 알림이 비활성화된 사용자가 FCM 토큰을 등록하려는 경우 발생하는 예외
 *
 * 발생 상황:
 * - notificationEnabled가 false인 사용자가 디바이스 등록 시도
 */
public class NotificationDisabledException extends DomainException {

    public NotificationDisabledException() {
        super(ErrorCode.NOTIFICATION_DISABLED, "알림이 비활성화되어 있습니다.");
    }
}