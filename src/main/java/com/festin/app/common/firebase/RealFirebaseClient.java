package com.festin.app.common.firebase;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 실제 Firebase Messaging 클라이언트
 *
 * firebase.enabled=true일 때 활성화됩니다.
 * Firebase Admin SDK를 사용하여 실제 FCM 푸시 알림을 발송합니다.
 */
@Component
@ConditionalOnProperty(
        prefix = "firebase",
        name = "enabled",
        havingValue = "true"
)
public class RealFirebaseClient implements FirebaseClient {

    @Override
    public String send(Message message) throws FirebaseMessagingException {
        return FirebaseMessaging.getInstance().send(message);
    }
}