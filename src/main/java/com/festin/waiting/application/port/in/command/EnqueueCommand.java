package com.festin.app.application.port.in.command;

/**
 * 대기 등록 Command
 *
 * Controller → UseCase로 전달되는 입력 데이터
 */
public record EnqueueCommand(
    Long userId,
    Long boothId
) {
}