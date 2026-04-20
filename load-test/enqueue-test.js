import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';

// ============================================
// 대기 등록 + 순번 조회 부하 테스트
// ============================================

// Custom Metrics
const enqueueSuccess = new Counter('enqueue_success');
const enqueueFail = new Counter('enqueue_fail');
const positionSuccess = new Counter('position_success');
const positionFail = new Counter('position_fail');
const enqueueLatency = new Trend('enqueue_latency');
const positionLatency = new Trend('position_latency');
const errorRate = new Rate('error_rate');

// Configuration
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const BOOTH_ID = __ENV.BOOTH_ID || 2;  // 부하테스트 전용 부스
const MAX_VUSERS = 500;  // 최대 VUser 수

// Test Stages: 점진적 부하 증가
export const options = {
    setupTimeout: '180s',  // 토큰 생성 시간 확보
    stages: [
        { duration: '30s', target: 10 },    // Warm-up: 10 VUsers
        { duration: '1m', target: 50 },     // Ramp-up: 50 VUsers
        { duration: '2m', target: 100 },    // Load: 100 VUsers
        { duration: '1m', target: 200 },    // Stress: 200 VUsers
        { duration: '30s', target: 0 },     // Cool-down
    ],
    thresholds: {
        http_req_duration: ['p(95)<2000'],  // 95% 요청이 2초 이내
        http_req_failed: ['rate<0.1'],       // 에러율 10% 미만
        enqueue_latency: ['p(95)<3000'],     // 대기등록 95% 3초 이내
        position_latency: ['p(95)<500'],     // 순번조회 95% 500ms 이내
    },
};

// Setup: 테스트용 JWT 토큰 여러 개 미리 발급
export function setup() {
    console.log(`🚀 Load Test Started - Target: ${BASE_URL}`);
    console.log(`📍 Test Booth ID: ${BOOTH_ID}`);
    console.log(`👥 Generating ${MAX_VUSERS} test users...`);

    const tokens = [];

    // 각 VUser마다 고유한 토큰 발급
    for (let i = 1; i <= MAX_VUSERS; i++) {
        const loginPayload = JSON.stringify({
            email: `loadtest_user_${i}@test.com`,
            nickname: `LoadTestUser${i}`,
            role: 'VISITOR',
            managedBoothId: null
        });

        const loginRes = http.post(
            `${BASE_URL}/api/v1/auth/login`,
            loginPayload,
            { headers: { 'Content-Type': 'application/json' } }
        );

        if (loginRes.status === 200) {
            try {
                const body = JSON.parse(loginRes.body);
                tokens.push({
                    userId: i,
                    token: body.accessToken || body.token,
                });
            } catch (e) {
                console.log(`⚠️ Failed to parse token for user ${i}`);
            }
        } else {
            console.log(`❌ Login failed for user ${i}: ${loginRes.status}`);
        }

        // 로그인 요청 간격 (서버 부하 방지)
        if (i % 50 === 0) {
            console.log(`✅ Generated ${i}/${MAX_VUSERS} tokens...`);
            sleep(0.5);
        }
    }

    console.log(`✅ Setup complete! Generated ${tokens.length} tokens`);

    return {
        baseUrl: BASE_URL,
        boothId: BOOTH_ID,
        tokens: tokens,
    };
}

// Main Test Scenario
export default function (data) {
    const vuId = __VU;  // Virtual User ID (1부터 시작)
    const boothId = data.boothId;

    // Setup에서 발급받은 토큰 사용 (VU ID에 맞는 토큰 선택)
    const tokenIndex = (vuId - 1) % data.tokens.length;
    const tokenData = data.tokens[tokenIndex];

    if (!tokenData || !tokenData.token) {
        console.log(`❌ No token available for VU ${vuId}`);
        return;
    }

    const headers = {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${tokenData.token}`,
    };

    // ============================================
    // Scenario 1: 대기 등록 (POST /api/v1/waitings)
    // ============================================
    const enqueuePayload = JSON.stringify({
        boothId: boothId,
    });

    const enqueueStart = Date.now();
    const enqueueRes = http.post(
        `${data.baseUrl}/api/v1/waitings`,
        enqueuePayload,
        { headers: headers, tags: { name: 'enqueue' } }
    );
    const enqueueEnd = Date.now();

    enqueueLatency.add(enqueueEnd - enqueueStart);

    const enqueueOk = check(enqueueRes, {
        'enqueue: status is 201': (r) => r.status === 201,
        'enqueue: has position': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.position !== undefined;
            } catch (e) {
                return false;
            }
        },
    });

    if (enqueueOk) {
        enqueueSuccess.add(1);
        errorRate.add(0);
    } else {
        enqueueFail.add(1);
        errorRate.add(1);

        // 에러 로깅 (409: 이미 등록됨, 400: 최대 대기 초과 등)
        if (enqueueRes.status === 409) {
            // 이미 등록된 경우 - 정상적인 비즈니스 로직
        } else if (enqueueRes.status !== 201) {
            console.log(`❌ Enqueue failed - VU: ${vuId}, Status: ${enqueueRes.status}, Body: ${enqueueRes.body}`);
        }
    }

    sleep(1);

    // ============================================
    // Scenario 2: 순번 조회 (GET /api/v1/waitings/booth/{boothId})
    // - 사용자들이 자주 새로고침하는 시나리오
    // ============================================
    for (let i = 0; i < 3; i++) {  // 3번 연속 조회 (폴링 시뮬레이션)
        const positionStart = Date.now();
        const positionRes = http.get(
            `${data.baseUrl}/api/v1/waitings/booth/${boothId}`,
            { headers: headers, tags: { name: 'position' } }
        );
        const positionEnd = Date.now();

        positionLatency.add(positionEnd - positionStart);

        const positionOk = check(positionRes, {
            'position: status is 200': (r) => r.status === 200,
            'position: has position field': (r) => {
                try {
                    const body = JSON.parse(r.body);
                    return body.position !== undefined || body.message !== undefined;
                } catch (e) {
                    return false;
                }
            },
        });

        if (positionOk) {
            positionSuccess.add(1);
        } else {
            positionFail.add(1);
            console.log(`❌ Position check failed - VU: ${vuId}, Status: ${positionRes.status}`);
        }

        sleep(0.5);  // 500ms 간격으로 폴링
    }

    sleep(1);
}

// Teardown - 테스트 데이터 정리
export function teardown(data) {
    console.log('🏁 Load Test Completed');
    console.log('🧹 Cleaning up test data...');

    // 각 테스트 유저의 대기 취소 (Redis 정리)
    for (const tokenData of data.tokens) {
        const headers = {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${tokenData.token}`,
        };

        // 대기 취소 API 호출
        http.request(
            'DELETE',
            `${data.baseUrl}/api/v1/waitings/${data.boothId}`,
            null,
            { headers: headers }
        );
    }

    console.log('✅ Cleanup completed!');
}