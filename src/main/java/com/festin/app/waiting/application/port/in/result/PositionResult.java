package com.festin.waiting.application.port.in.result;

/**
 * 순번 조회 Result
 *
 * UseCase → Controller로 반환되는 결과 데이터
 */
public record PositionResult(
    Long boothId,
    String boothName,
    Integer position,           // 현재 순번
    Integer totalWaiting,       // 전체 대기자 수
    Integer estimatedWaitTime   // 예상 대기 시간 (분)
) {
}