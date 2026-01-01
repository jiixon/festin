package com.festin.app.common.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Firebase 초기화 설정
 *
 * FCM (Firebase Cloud Messaging) 사용을 위한 Firebase Admin SDK 초기화
 * - 환경변수 FIREBASE_CREDENTIALS_JSON에서 우선 읽음 (프로덕션)
 * - 없으면 파일 경로에서 읽음 (로컬 개발)
 */
@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${firebase.credentials.path:firebase-service-account.json}")
    private String credentialsPath;

    @Value("${firebase.credentials.json:}")
    private String credentialsJson;

    @Value("${firebase.enabled:false}")
    private boolean firebaseEnabled;

    @PostConstruct
    public void initialize() {
        if (!firebaseEnabled) {
            log.warn("Firebase가 비활성화되어 있습니다.");
            return;
        }

        try {
            InputStream serviceAccount = getCredentialsStream();

            if (serviceAccount == null) {
                log.warn("Firebase credentials를 찾을 수 없습니다.");
                log.warn("FCM 푸시 알림이 비활성화됩니다.");
                return;
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
                log.info("Firebase가 성공적으로 초기화되었습니다.");
            }

        } catch (IOException e) {
            log.error("Firebase 초기화 실패: {}", e.getMessage(), e);
            log.warn("FCM 푸시 알림이 비활성화됩니다.");
        }
    }

    private InputStream getCredentialsStream() throws IOException {
        // 1. 환경변수에서 JSON 읽기 (프로덕션)
        if (credentialsJson != null && !credentialsJson.isBlank()) {
            log.info("환경변수에서 Firebase credentials를 로드합니다.");
            return new ByteArrayInputStream(credentialsJson.getBytes());
        }

        // 2. 파일에서 읽기 (로컬 개발)
        ClassPathResource resource = new ClassPathResource(credentialsPath);
        if (resource.exists()) {
            log.info("파일에서 Firebase credentials를 로드합니다: {}", credentialsPath);
            return resource.getInputStream();
        }

        return null;
    }
}