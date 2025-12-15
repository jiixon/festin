package com.festin.app.waiting.application.port.in.result;

import java.time.LocalDateTime;

/**
 * 대기 등록 Result
 *
 * UseCase → Controller로 반환되는 결과 데이터
 */
public record EnqueueResult(
    Long boothId,
    String boothName,
    Integer position,           // 현재 순번
    Integer totalWaiting,       // 전체 대기자 수
    Integer estimatedWaitTime,  // 예상 대기 시간 (분)
    LocalDateTime registeredAt  // 등록 시간
) {
}