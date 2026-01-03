package com.festin.app.waiting.application.port.out;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

/**
 * 대기열 캐시 Port
 *
 * 책임:
 * - Redis Sorted Set 기반 대기열 관리
 * - 등록 시각(score)으로 자동 정렬
 * - 순번 조회 및 대기자 수 조회
 * - 사용자별 활성 부스 목록 관리 (최대 2개 제한 검증)
 *
 * 구현체:
 * - RedisQueueAdapter
 *
 * Redis 키 형식:
 * - queue:booth:{boothId} (Sorted Set)
 *   - member: userId
 *   - score: 등록 시각 (timestamp)
 * - user:{userId}:active_booths (Set)
 *   - 사용자가 현재 대기 중인 부스 ID 목록
 */
public interface QueueCachePort {

    /**
     * 대기열 항목
     *
     * dequeue 시 사용자 ID와 등록 시각을 함께 반환
     *
     * @param userId 사용자 ID
     * @param registeredAt 등록 시각 (Redis Sorted Set의 score)
     */
    record QueueItem(
            Long userId,
            LocalDateTime registeredAt
    ) {
    }

    /**
     * 대기열에 사용자 추가
     *
     * @param boothId 부스 ID
     * @param userId 사용자 ID
     * @param registeredAt 등록 시각
     * @return 추가 성공 여부
     */
    boolean enqueue(Long boothId, Long userId, LocalDateTime registeredAt);

    /**
     * 대기열에서 다음 사용자 가져오기 (제거)
     *
     * Redis ZPOPMIN을 사용하여 원자적으로 처리
     * - userId (member)와 registeredAt (score)을 함께 반환
     * - 1회 Redis 호출로 처리 (성능 최적화)
     * - Race Condition 없음
     *
     * @param boothId 부스 ID
     * @return 대기열 항목 (userId, registeredAt)
     */
    Optional<QueueItem> dequeue(Long boothId);

    /**
     * 사용자의 대기 순번 조회
     *
     * @param boothId 부스 ID
     * @param userId 사용자 ID
     * @return 순번 (1부터 시작, 없으면 empty)
     */
    Optional<Integer> getPosition(Long boothId, Long userId);

    /**
     * 대기열 전체 인원 수
     *
     * @param boothId 부스 ID
     * @return 대기 인원 수
     */
    int getQueueSize(Long boothId);

    /**
     * 대기열에서 사용자 제거
     *
     * @param boothId 부스 ID
     * @param userId 사용자 ID
     * @return 제거 성공 여부
     */
    boolean remove(Long boothId, Long userId);

    /**
     * 사용자의 대기 등록 시간 조회
     *
     * @param boothId 부스 ID
     * @param userId 사용자 ID
     * @return 등록 시간 (없으면 empty)
     */
    Optional<LocalDateTime> getRegisteredAt(Long boothId, Long userId);

    /**
     * 사용자가 대기 중인 부스 개수 조회
     *
     * @param userId 사용자 ID
     * @return 활성 부스 개수
     */
    int getUserActiveBoothCount(Long userId);

    /**
     * 사용자의 활성 부스 목록에 부스 추가
     *
     * @param userId 사용자 ID
     * @param boothId 부스 ID
     */
    void addUserActiveBooth(Long userId, Long boothId);

    /**
     * 사용자의 활성 부스 목록에서 부스 제거
     *
     * @param userId 사용자 ID
     * @param boothId 부스 ID
     */
    void removeUserActiveBooth(Long userId, Long boothId);

    /**
     * 사용자의 활성 부스 목록 조회
     *
     * @param userId 사용자 ID
     * @return 활성 부스 ID 목록
     */
    Set<Long> getUserActiveBooths(Long userId);

    /**
     * 원자적 대기 등록 (Lua Script)
     *
     * Race Condition 방지:
     * - activeCount 체크 → 중복 체크 → enqueue → addActiveBooth를 단일 원자적 작업으로 처리
     * - Redis 단일 스레드 특성으로 완벽한 원자성 보장
     *
     * @param boothId 부스 ID
     * @param userId 사용자 ID
     * @param registeredAt 등록 시각
     * @param maxActiveBooths 최대 활성 부스 수
     * @return 등록 결과 (상태, 순번, 전체 대기자 수)
     */
    EnqueueAtomicResult enqueueAtomic(Long boothId, Long userId, LocalDateTime registeredAt, int maxActiveBooths);

    /**
     * 원자적 등록 결과
     *
     * @param status 등록 상태
     * @param position 순번 (1부터 시작, 실패 시 -1)
     * @param totalWaiting 전체 대기자 수 (실패 시 -1)
     */
    record EnqueueAtomicResult(
            EnqueueStatus status,
            Integer position,
            Integer totalWaiting
    ) {
    }

    /**
     * 원자적 등록 상태
     */
    enum EnqueueStatus {
        /**
         * 성공 (신규 등록)
         */
        SUCCESS,

        /**
         * 이미 등록되어 있음 (멱등성)
         */
        ALREADY_ENQUEUED,

        /**
         * 최대 활성 부스 수 초과
         */
        MAX_BOOTHS_EXCEEDED
    }

    /**
     * Soft Lock 데이터
     *
     * MySQL save 실패 지점을 마킹하기 위한 데이터
     * - timestamp 보존으로 정확한 위치 복구 가능
     * - 배치가 Soft Lock을 스캔하여 정합성 보정
     *
     * @param boothId 부스 ID
     * @param userId 사용자 ID
     * @param registeredAt 원래 등록 시각 (Redis dequeue 시점의 timestamp)
     * @param createdAt Soft Lock 생성 시각
     */
    record SoftLockData(
            Long boothId,
            Long userId,
            LocalDateTime registeredAt,
            LocalDateTime createdAt
    ) {
    }

    /**
     * Soft Lock 생성
     *
     * callNext 실패 지점 마킹:
     * - Redis dequeue 직후 생성
     * - MySQL save 실패 시 Soft Lock 남김
     * - 배치가 Soft Lock 스캔하여 Redis 롤백
     *
     * Redis 키: temp:calling:{boothId}:{userId}
     *
     * @param boothId 부스 ID
     * @param userId 사용자 ID
     * @param registeredAt 원래 등록 시각 (복구 시 사용)
     */
    void createSoftLock(Long boothId, Long userId, LocalDateTime registeredAt);

    /**
     * Soft Lock 삭제
     *
     * MySQL save 성공 시 호출:
     * - 정상 처리 완료 표시
     * - Soft Lock 제거
     *
     * @param boothId 부스 ID
     * @param userId 사용자 ID
     */
    void deleteSoftLock(Long boothId, Long userId);

    /**
     * Soft Lock 조회
     *
     * 배치 보정용:
     * - Soft Lock 존재 여부 확인
     * - timestamp 조회하여 Redis 롤백
     *
     * @param boothId 부스 ID
     * @param userId 사용자 ID
     * @return Soft Lock 데이터, 없으면 empty
     */
    Optional<SoftLockData> getSoftLock(Long boothId, Long userId);
}
