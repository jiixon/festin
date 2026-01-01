package com.festin.app.waiting.application.port.out;

/**
 * 알림 중복 방지 Port
 *
 * Redis를 사용하여 동일한 알림이 중복 발송되지 않도록 합니다.
 */
public interface NotificationIdempotencyPort {

    /**
     * 이벤트 처리 여부 확인 및 처리 시작
     *
     * @param eventId 이벤트 ID
     * @return true: 처음 처리 / false: 이미 처리됨
     */
    boolean tryProcess(String eventId);

    /**
     * 이벤트 처리 완료 기록
     *
     * @param eventId 이벤트 ID
     */
    void markProcessed(String eventId);
}