package com.festin.app.common.firebase;

import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;

/**
 * Firebase Messaging 클라이언트 인터페이스
 *
 * 구현체:
 * - RealFirebaseClient: 실제 FCM 발송 (firebase.enabled=true)
 * - NoopFirebaseClient: Stub (firebase.enabled=false, 로컬 개발용)
 */
public interface FirebaseClient {

    /**
     * FCM 메시지 전송
     *
     * @param message FCM 메시지
     * @return 메시지 ID
     * @throws FirebaseMessagingException FCM 전송 실패
     */
    String send(Message message) throws FirebaseMessagingException;
}