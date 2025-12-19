package com.festin.app.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 에러 코드 정의
 *
 * API 응답 시 사용되는 표준 에러 코드
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 400 Bad Request
    INVALID_STATUS(400, "INVALID_STATUS", "잘못된 상태입니다."),
    INVALID_REQUEST(400, "INVALID_REQUEST", "잘못된 요청입니다."),

    // 404 Not Found
    BOOTH_NOT_FOUND(404, "BOOTH_NOT_FOUND", "부스를 찾을 수 없습니다."),
    USER_NOT_FOUND(404, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
    WAITING_NOT_FOUND(404, "WAITING_NOT_FOUND", "해당 부스에 대기 중이 아닙니다."),
    QUEUE_EMPTY(404, "QUEUE_EMPTY", "대기 중인 사람이 없습니다."),

    // 409 Conflict
    MAX_WAITING_EXCEEDED(409, "MAX_WAITING_EXCEEDED", "최대 2개 부스까지만 대기 가능합니다."),
    BOOTH_CLOSED(409, "BOOTH_CLOSED", "부스가 운영 중이 아닙니다."),
    BOOTH_FULL(409, "BOOTH_FULL", "부스 정원이 초과되었습니다."),

    // 500 Internal Server Error
    QUEUE_OPERATION_ERROR(500, "QUEUE_OPERATION_ERROR", "대기열 처리 중 오류가 발생했습니다."),
    INTERNAL_SERVER_ERROR(500, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다.");

    private final int status;
    private final String code;
    private final String message;
}