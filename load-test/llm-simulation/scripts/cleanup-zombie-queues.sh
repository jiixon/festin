#!/usr/bin/env bash
# 시뮬레이션 시작 전 Redis 좀비 큐 정리
#
# 시나리오: RDS users 에서 수동 DELETE 가 일어났는데 Redis queue:booth:* 에
# 좀비 user_id 가 남아있으면 staff /waitings/call 호출 시 FK 위반으로 500.
# 이 스크립트는 Redis 모든 부스 큐를 훑어 RDS 에 없는 user_id 를 ZREM 한다.
#
# 사용법 (EC2 에서):
#   export RDS_HOST=festin-mysql.xxxx.rds.amazonaws.com
#   export RDS_PASSWORD=********
#   ./scripts/cleanup-zombie-queues.sh
#
# 선택 env:
#   RDS_USER         (default: admin)
#   RDS_DB           (default: festin)
#   REDIS_CONTAINER  (default: festin-redis)

set -euo pipefail

: "${RDS_HOST:?RDS_HOST env var required}"
: "${RDS_PASSWORD:?RDS_PASSWORD env var required}"
RDS_USER="${RDS_USER:-admin}"
RDS_DB="${RDS_DB:-festin}"
REDIS_CONTAINER="${REDIS_CONTAINER:-festin-redis}"

TMPDIR=$(mktemp -d)
trap 'rm -rf "$TMPDIR"' EXIT

echo "[1/4] 부스 큐 탐색"
docker exec "$REDIS_CONTAINER" redis-cli --scan --pattern 'queue:booth:*' \
  | sort -u > "$TMPDIR/queue_keys.txt"
NUM_QUEUES=$(wc -l < "$TMPDIR/queue_keys.txt")
echo "      큐 발견: $NUM_QUEUES 개"

if [ "$NUM_QUEUES" -eq 0 ]; then
  echo "[Done] 큐 없음 — 종료"
  exit 0
fi

echo "[2/4] Redis 큐 멤버 수집"
while IFS= read -r key; do
  docker exec "$REDIS_CONTAINER" redis-cli ZRANGE "$key" 0 -1
done < "$TMPDIR/queue_keys.txt" | sort -un > "$TMPDIR/redis_ids.txt"
echo "      Redis user_id (unique): $(wc -l < "$TMPDIR/redis_ids.txt") 개"

echo "[3/4] RDS 실재 user_id 조회"
MYSQL_PWD="$RDS_PASSWORD" mysql -h "$RDS_HOST" -u "$RDS_USER" -N -B \
  -e "SELECT id FROM users" "$RDS_DB" | sort -un > "$TMPDIR/real_ids.txt"
echo "      RDS users: $(wc -l < "$TMPDIR/real_ids.txt") 명"

echo "[4/4] 좀비 판정 및 제거"
comm -23 "$TMPDIR/redis_ids.txt" "$TMPDIR/real_ids.txt" > "$TMPDIR/zombies.txt"
ZOMBIES=$(wc -l < "$TMPDIR/zombies.txt")
echo "      좀비 발견: $ZOMBIES 명"

if [ "$ZOMBIES" -eq 0 ]; then
  echo "[Done] 청소 불필요 ✓"
  exit 0
fi

TOTAL_REMOVED=0
while IFS= read -r uid; do
  while IFS= read -r key; do
    r=$(docker exec "$REDIS_CONTAINER" redis-cli ZREM "$key" "$uid")
    TOTAL_REMOVED=$((TOTAL_REMOVED + r))
  done < "$TMPDIR/queue_keys.txt"
done < "$TMPDIR/zombies.txt"

echo "[Done] ZREM 성공 $TOTAL_REMOVED 건 (좀비 $ZOMBIES 명)"