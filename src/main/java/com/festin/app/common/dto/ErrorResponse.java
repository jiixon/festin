package com.festin.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.festin.common.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Map;

/**
 * 표준 에러 응답 DTO
 *
 * API 명세에 정의된 에러 응답 포맷
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private final int status;
    private final String code;
    private final String message;
    private final Map<String, Object> details;

    /**
     * ErrorCode로부터 ErrorResponse 생성
     */
    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(
                errorCode.getStatus(),
                errorCode.getCode(),
                errorCode.getMessage(),
                null
        );
    }

    /**
     * ErrorCode와 커스텀 메시지로 ErrorResponse 생성
     */
    public static ErrorResponse of(ErrorCode errorCode, String customMessage) {
        return new ErrorResponse(
                errorCode.getStatus(),
                errorCode.getCode(),
                customMessage,
                null
        );
    }

    /**
     * ErrorCode와 details로 ErrorResponse 생성
     */
    public static ErrorResponse of(ErrorCode errorCode, Map<String, Object> details) {
        return new ErrorResponse(
                errorCode.getStatus(),
                errorCode.getCode(),
                errorCode.getMessage(),
                details
        );
    }

    /**
     * ErrorCode, 커스텀 메시지, details로 ErrorResponse 생성
     */
    public static ErrorResponse of(ErrorCode errorCode, String customMessage, Map<String, Object> details) {
        return new ErrorResponse(
                errorCode.getStatus(),
                errorCode.getCode(),
                customMessage,
                details
        );
    }
}