--[[
대기 등록 원자적 처리 Lua Script

Race Condition 방지:
- activeCount 체크 → 중복 체크 → enqueue → addActiveBooth를 단일 원자적 작업으로 처리
- Redis 단일 스레드 특성으로 원자성 보장

KEYS:
- KEYS[1]: queue:booth:{boothId} (Sorted Set - 대기열)
- KEYS[2]: user:{userId}:active_booths (Set - 활성 부스 목록)

ARGV:
- ARGV[1]: userId (대기 등록할 사용자 ID)
- ARGV[2]: boothId (대기 등록할 부스 ID)
- ARGV[3]: timestamp (등록 시각의 epoch seconds)
- ARGV[4]: maxActiveBooths (최대 활성 부스 수, 기본 2)

Return:
- {1, position, totalWaiting}: 성공 (신규 등록)
- {2, position, totalWaiting}: 이미 등록됨 (멱등성)
- {0, -1, -1}: 실패 (최대 활성 부스 수 초과)
]]

local queueKey = KEYS[1]
local activeBoothsKey = KEYS[2]

local userId = ARGV[1]
local boothId = ARGV[2]
local timestamp = tonumber(ARGV[3])
local maxActiveBooths = tonumber(ARGV[4])

-- 1. 중복 등록 체크 (멱등성)
local existingRank = redis.call('ZRANK', queueKey, userId)
if existingRank then
    -- 이미 등록되어 있음 - 현재 정보 반환
    local position = existingRank + 1  -- rank는 0부터 시작
    local totalWaiting = redis.call('ZCARD', queueKey)
    return {2, position, totalWaiting}
end

-- 2. 활성 부스 개수 체크
local activeCount = redis.call('SCARD', activeBoothsKey)
if activeCount >= maxActiveBooths then
    -- 최대 부스 수 초과
    return {0, -1, -1}
end

-- 3. 대기열에 추가 (신규 등록)
redis.call('ZADD', queueKey, timestamp, userId)

-- 4. 활성 부스 목록에 추가
redis.call('SADD', activeBoothsKey, boothId)

-- 5. 최종 순번 및 대기자 수 조회
local finalRank = redis.call('ZRANK', queueKey, userId)
local position = finalRank + 1
local totalWaiting = redis.call('ZCARD', queueKey)

-- 성공
return {1, position, totalWaiting}