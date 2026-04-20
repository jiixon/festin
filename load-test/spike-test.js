import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

// ============================================
// 스파이크 테스트: 축제 오픈 시 순간 폭주 시뮬레이션
// ============================================

const enqueueLatency = new Trend('enqueue_latency');
const enqueueSuccess = new Counter('enqueue_success');
const enqueueFail = new Counter('enqueue_fail');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const BOOTH_ID = __ENV.BOOTH_ID || 2;  // 부하테스트 전용 부스
const MAX_VUSERS = 1000;

export const options = {
    setupTimeout: '300s',  // 1000명 토큰 생성 시간 확보
    teardownTimeout: '120s',
    scenarios: {
        spike: {
            executor: 'ramping-arrival-rate',
            startRate: 1,
            timeUnit: '1s',
            preAllocatedVUs: 500,
            maxVUs: 1000,
            stages: [
                { duration: '10s', target: 10 },    // 천천히 시작
                { duration: '5s', target: 500 },    // 급격히 증가 (스파이크!)
                { duration: '30s', target: 500 },   // 고부하 유지
                { duration: '10s', target: 10 },    // 감소
                { duration: '10s', target: 0 },     // 종료
            ],
        },
    },
    thresholds: {
        http_req_duration: ['p(99)<5000'],  // 99% 요청이 5초 이내
        http_req_failed: ['rate<0.2'],       // 에러율 20% 미만 (스파이크는 관대하게)
    },
};

// Setup: 테스트용 JWT 토큰 여러 개 미리 발급
export function setup() {
    console.log(`⚡ Spike Test Started - Simulating festival opening`);
    console.log(`📍 Target: ${BASE_URL}, Booth: ${BOOTH_ID}`);
    console.log(`👥 Generating ${MAX_VUSERS} test users...`);

    const tokens = [];

    for (let i = 1; i <= MAX_VUSERS; i++) {
        const loginPayload = JSON.stringify({
            email: `spiketest_user_${i}@test.com`,
            nickname: `SpikeTestUser${i}`,
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
                // ignore
            }
        }

        if (i % 100 === 0) {
            console.log(`✅ Generated ${i}/${MAX_VUSERS} tokens...`);
            sleep(0.3);
        }
    }

    console.log(`✅ Setup complete! Generated ${tokens.length} tokens`);

    return {
        baseUrl: BASE_URL,
        boothId: BOOTH_ID,
        tokens: tokens,
    };
}

export default function (data) {
    // 토큰 선택 (VU ID 기반)
    const tokenIndex = (__VU - 1) % data.tokens.length;
    const tokenData = data.tokens[tokenIndex];

    if (!tokenData || !tokenData.token) {
        enqueueFail.add(1);
        return;
    }

    const headers = {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${tokenData.token}`,
    };

    const payload = JSON.stringify({ boothId: data.boothId });

    const start = Date.now();
    const res = http.post(
        `${data.baseUrl}/api/v1/waitings`,
        payload,
        { headers: headers }
    );
    const duration = Date.now() - start;

    enqueueLatency.add(duration);

    if (check(res, { 'status is 201 or 409': (r) => r.status === 201 || r.status === 409 })) {
        enqueueSuccess.add(1);
    } else {
        enqueueFail.add(1);
        if (res.status >= 500) {
            console.log(`🔥 Server Error: ${res.status} - ${res.body}`);
        }
    }
}

// Teardown - 테스트 데이터 정리
export function teardown(data) {
    console.log('⚡ Spike Test Completed');
    console.log('🧹 Cleaning up test data...');

    for (const tokenData of data.tokens) {
        const headers = {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${tokenData.token}`,
        };

        http.request(
            'DELETE',
            `${data.baseUrl}/api/v1/waitings/${data.boothId}`,
            null,
            { headers: headers }
        );
    }

    console.log('✅ Cleanup completed!');
}