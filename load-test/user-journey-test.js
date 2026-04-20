import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

// ============================================
// 복합 시나리오: 실제 축제 유저 여정 시뮬레이션
//
// 유저 행동 패턴:
// 1. 축제 입장 → 부스 목록 조회
// 2. 인기 부스 발견 → 대기 등록
// 3. 대기 중 → 순번 조회 반복 (10초마다)
// 4. 다른 부스도 등록 (최대 2개)
// 5. 대기 취소 후 퇴장
// ============================================

// Custom Metrics
const boothListLatency = new Trend('booth_list_latency');
const enqueueLatency = new Trend('enqueue_latency');
const positionLatency = new Trend('position_latency');
const journeySuccess = new Counter('journey_success');
const journeyFail = new Counter('journey_fail');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const BOOTH_ID_1 = 2;  // 부하테스트 전용 부스
const BOOTH_ID_2 = 3;  // 두번째 부하테스트 전용 부스

export const options = {
    setupTimeout: '180s',
    teardownTimeout: '300s',
    scenarios: {
        // 실제 축제 상황: 점진적으로 사람들이 입장
        festival_opening: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 50 },   // 축제 오픈, 사람들 입장
                { duration: '1m', target: 100 },   // 점점 붐빔
                { duration: '2m', target: 100 },   // 피크 타임 유지
                { duration: '30s', target: 0 },    // 종료
            ],
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<2000'],
        http_req_failed: ['rate<0.2'],
        booth_list_latency: ['p(95)<1000'],
        enqueue_latency: ['p(95)<2000'],
        position_latency: ['p(95)<500'],
    },
};

// Setup: 테스트 유저 토큰 생성
export function setup() {
    console.log(`🎪 Festival User Journey Test Started`);
    console.log(`📍 Target: ${BASE_URL}`);

    const tokens = [];
    const MAX_USERS = 200;

    console.log(`👥 Generating ${MAX_USERS} visitor tokens...`);

    for (let i = 1; i <= MAX_USERS; i++) {
        const payload = JSON.stringify({
            email: `festival_visitor_${i}@test.com`,
            nickname: `Visitor${i}`,
            role: 'VISITOR',
            managedBoothId: null
        });

        const res = http.post(
            `${BASE_URL}/api/v1/auth/login`,
            payload,
            { headers: { 'Content-Type': 'application/json' } }
        );

        if (res.status === 200) {
            const body = JSON.parse(res.body);
            tokens.push(body.accessToken || body.token);
        }

        if (i % 50 === 0) {
            console.log(`✅ Generated ${i}/${MAX_USERS} tokens`);
            sleep(0.3);
        }
    }

    console.log(`✅ Setup complete! ${tokens.length} visitors ready`);

    return { baseUrl: BASE_URL, tokens: tokens };
}

// Main: 유저 여정 시뮬레이션
export default function (data) {
    const tokenIndex = (__VU - 1) % data.tokens.length;
    const token = data.tokens[tokenIndex];

    if (!token) {
        journeyFail.add(1);
        return;
    }

    const headers = {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
    };

    let journeyOk = true;

    // ========================================
    // Step 1: 부스 목록 조회 (축제 입장)
    // ========================================
    const listStart = Date.now();
    const listRes = http.get(
        `${data.baseUrl}/api/v1/booths`,
        { headers: headers, tags: { name: 'booth_list' } }
    );
    boothListLatency.add(Date.now() - listStart);

    journeyOk = check(listRes, {
        'booth list: status 200': (r) => r.status === 200,
    }) && journeyOk;

    sleep(Math.random() * 2 + 1);  // 부스 목록 구경 (1-3초)

    // ========================================
    // Step 2: 첫 번째 부스 대기 등록
    // ========================================
    const enqueue1Start = Date.now();
    const enqueue1Res = http.post(
        `${data.baseUrl}/api/v1/waitings`,
        JSON.stringify({ boothId: BOOTH_ID_1 }),
        { headers: headers, tags: { name: 'enqueue' } }
    );
    enqueueLatency.add(Date.now() - enqueue1Start);

    const enqueue1Ok = check(enqueue1Res, {
        'enqueue 1: status 201 or already registered': (r) => r.status === 201 || r.status === 409,
    });
    journeyOk = enqueue1Ok && journeyOk;

    sleep(1);

    // ========================================
    // Step 3: 순번 조회 반복 (대기 중 새로고침)
    // ========================================
    for (let i = 0; i < 5; i++) {
        const posStart = Date.now();
        const posRes = http.get(
            `${data.baseUrl}/api/v1/waitings/booth/${BOOTH_ID_1}`,
            { headers: headers, tags: { name: 'position' } }
        );
        positionLatency.add(Date.now() - posStart);

        check(posRes, {
            'position: status 200 or 404': (r) => r.status === 200 || r.status === 404,
        });

        sleep(2);  // 2초마다 새로고침 (실제는 10초)
    }

    // ========================================
    // Step 4: 두 번째 부스도 등록 시도 (최대 2개 제한)
    // ========================================
    const enqueue2Start = Date.now();
    const enqueue2Res = http.post(
        `${data.baseUrl}/api/v1/waitings`,
        JSON.stringify({ boothId: BOOTH_ID_2 }),
        { headers: headers, tags: { name: 'enqueue' } }
    );
    enqueueLatency.add(Date.now() - enqueue2Start);

    check(enqueue2Res, {
        'enqueue 2: status 201/409/400': (r) => r.status === 201 || r.status === 409 || r.status === 400,
    });

    sleep(Math.random() * 3 + 2);  // 추가 대기 (2-5초)

    // ========================================
    // Step 5: 대기 취소 후 퇴장
    // ========================================
    http.request(
        'DELETE',
        `${data.baseUrl}/api/v1/waitings/${BOOTH_ID_1}`,
        null,
        { headers: headers, tags: { name: 'cancel' } }
    );

    http.request(
        'DELETE',
        `${data.baseUrl}/api/v1/waitings/${BOOTH_ID_2}`,
        null,
        { headers: headers, tags: { name: 'cancel' } }
    );

    if (journeyOk) {
        journeySuccess.add(1);
    } else {
        journeyFail.add(1);
    }
}

// Teardown
export function teardown(data) {
    console.log('🎪 Festival User Journey Test Completed');
    console.log('🧹 Cleaning up...');

    // 남은 대기열 정리
    for (const token of data.tokens) {
        const headers = { 'Authorization': `Bearer ${token}` };
        http.request('DELETE', `${data.baseUrl}/api/v1/waitings/${BOOTH_ID_1}`, null, { headers });
        http.request('DELETE', `${data.baseUrl}/api/v1/waitings/${BOOTH_ID_2}`, null, { headers });
    }

    console.log('✅ Cleanup completed');
}