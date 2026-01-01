package com.festin.app.common.firebase;

import com.google.firebase.messaging.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Noop Firebase Messaging 클라이언트 (Stub)
 *
 * firebase.enabled=false일 때 활성화됩니다.
 * 실제 FCM 발송 없이 로그만 출력합니다. (로컬 개발 환경용)
 */
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "firebase",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class NoopFirebaseClient implements FirebaseClient {

    @Override
    public String send(Message message) {
        String mockMessageId = "mock-" + UUID.randomUUID().toString().substring(0, 8);

        log.info("[NOOP] FCM 푸시 알림 발송 (실제 발송 안 함) - messageId: {}", mockMessageId);

        return mockMessageId;
    }
}