package com.festin.app.waiting.application.port.out;

import java.time.LocalDateTime;
import java.util.Optional;

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
     * @param boothId 부스 ID
     * @return 다음 사용자 ID
     */
    Optional<Long> dequeue(Long boothId);

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
}
