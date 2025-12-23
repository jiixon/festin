package com.festin.app.common;

import com.festin.app.common.dto.ErrorResponse;
import com.festin.app.common.exception.DomainException;
import com.festin.app.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 전역 예외 핸들러
 *
 * 모든 Controller에서 발생하는 예외를 처리하여 표준화된 에러 응답 반환
 * Actuator 엔드포인트는 제외
 */
@Slf4j
@RestControllerAdvice(
        basePackages = "com.festin.app"
)
public class GlobalExceptionHandler {

    /**
     * DomainException 처리
     *
     * 모든 도메인 예외를 처리하여 ErrorCode에 정의된 상태 코드와 메시지로 응답
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(DomainException e) {
        ErrorCode errorCode = e.getErrorCode();

        log.warn("Domain exception occurred: code={}, message={}",
                errorCode.getCode(), e.getMessage());

        ErrorResponse response = ErrorResponse.of(errorCode, e.getMessage());

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(response);
    }

    /**
     * 예상하지 못한 예외 처리
     *
     * DomainException이 아닌 예외는 500 Internal Server Error로 응답
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception e) {
        log.error("Unexpected exception occurred", e);

        ErrorResponse response = ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR);

        return ResponseEntity
                .status(500)
                .body(response);
    }
}