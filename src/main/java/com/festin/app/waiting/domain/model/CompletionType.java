package com.festin.app.waiting.domain.model;

/**
 * 완료 유형
 *
 * 대기가 어떻게 종료되었는지 구분
 */
public enum CompletionType {
    ENTERED,     // 정상 체험 완료
    NO_SHOW,     // 타임아웃 미입장 (5분)
    CANCELLED    // 사용자 취소
}