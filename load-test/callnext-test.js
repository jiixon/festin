import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

// ============================================
// CallNext 부하 테스트
// 시나리오: 여러 부스 운영자가 동시에 손님 호출
// ============================================

const callLatency = new Trend('call_latency');
const callSuccess = new Counter('call_success');
const callFail = new Counter('call_fail');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const BOOTH_ID = __ENV.BOOTH_ID || 2;

export const options = {
    setupTimeout: '180s',
    teardownTimeout: '60s',
    scenarios: {
        // 시나리오: 동시성 스트레스 테스트
        concurrent_call: {
            executor: 'constant-vus',
            vus: 20,  // 20명이 동시에 (빡센 테스트)
            duration: '30s',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<2000'],  // 95% 요청이 2초 이내
        http_req_failed: ['rate<0.3'],       // 에러율 30% 미만 (대기열 비면 에러)
    },
};

// Setup: 테스트 데이터 준비
// 1. 부스 운영자 토큰 발급
// 2. 테스트 유저들 대기 등록
export function setup() {
    console.log(`🔔 CallNext Test Started - Target: ${BASE_URL}`);
    console.log(`📍 Booth ID: ${BOOTH_ID}`);

    // 1. 부스 운영자 토큰 발급
    const staffPayload = JSON.stringify({
        email: `booth_staff_${BOOTH_ID}@test.com`,
        nickname: `BoothStaff${BOOTH_ID}`,
        role: 'STAFF',
        managedBoothId: BOOTH_ID
    });

    const staffRes = http.post(
        `${BASE_URL}/api/v1/auth/login`,
        staffPayload,
        { headers: { 'Content-Type': 'application/json' } }
    );

    let staffToken = null;
    if (staffRes.status === 200) {
        const body = JSON.parse(staffRes.body);
        staffToken = body.accessToken || body.token;
        console.log('✅ Staff token generated');
    } else {
        console.log(`❌ Staff login failed: ${staffRes.status}`);
    }

    // 2. 테스트 유저들 대기 등록 (호출할 대상)
    console.log('👥 Generating waiting users...');
    const userTokens = [];

    for (let i = 1; i <= 100; i++) {
        const userPayload = JSON.stringify({
            email: `calltest_user_${i}@test.com`,
            nickname: `CallTestUser${i}`,
            role: 'VISITOR',
            managedBoothId: null
        });

        const userRes = http.post(
            `${BASE_URL}/api/v1/auth/login`,
            userPayload,
            { headers: { 'Content-Type': 'application/json' } }
        );

        if (userRes.status === 200) {
            const body = JSON.parse(userRes.body);
            const token = body.accessToken || body.token;
            userTokens.push(token);

            // 대기 등록
            const enqueueRes = http.post(
                `${BASE_URL}/api/v1/waitings`,
                JSON.stringify({ boothId: BOOTH_ID }),
                { headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` } }
            );

            if (enqueueRes.status !== 201) {
                console.log(`⚠️ Enqueue failed for user ${i}: ${enqueueRes.status} - ${enqueueRes.body}`);
            }
        }

        if (i % 20 === 0) {
            console.log(`✅ Registered ${i}/100 waiting users`);
            sleep(0.3);
        }
    }

    console.log(`✅ Setup complete! ${userTokens.length} users waiting`);

    return {
        baseUrl: BASE_URL,
        boothId: BOOTH_ID,
        staffToken: staffToken,
        userTokens: userTokens,
    };
}

// Main: CallNext 호출
export default function (data) {
    if (!data.staffToken) {
        console.log('❌ No staff token');
        return;
    }

    const headers = {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${data.staffToken}`,
    };

    const start = Date.now();
    const res = http.post(
        `${data.baseUrl}/api/v1/waitings/call`,
        JSON.stringify({ boothId: data.boothId }),
        { headers: headers }
    );
    const duration = Date.now() - start;

    callLatency.add(duration);

    const ok = check(res, {
        'status is 200 or 404': (r) => r.status === 200 || r.status === 404,  // 404: 대기열 비었음
    });

    if (ok && res.status === 200) {
        callSuccess.add(1);
    } else if (res.status === 404) {
        // 대기열 비었음 - 정상적인 상황
        console.log('📭 Queue empty');
    } else {
        callFail.add(1);
        console.log(`❌ CallNext failed: ${res.status} - ${res.body}`);
    }

    // 동시성 테스트: 빠르게 호출
    sleep(0.1);
}

// Teardown: 정리
export function teardown(data) {
    console.log('🔔 CallNext Test Completed');

    // 남은 대기열 정리
    for (const token of data.userTokens) {
        http.request(
            'DELETE',
            `${data.baseUrl}/api/v1/waitings/${data.boothId}`,
            null,
            { headers: { 'Authorization': `Bearer ${token}` } }
        );
    }

    console.log('✅ Cleanup completed');
}