package com.festin.app.waiting.application.event;

/**
 * CallNext 이벤트
 *
 * TransactionalEventListener 패턴:
 * - MySQL 커밋 후 Redis 변경을 수행하기 위한 이벤트
 * - @TransactionalEventListener(AFTER_COMMIT)에서 처리
 */
public record CallNextEvent(
        Long boothId,
        Long userId
) {
}