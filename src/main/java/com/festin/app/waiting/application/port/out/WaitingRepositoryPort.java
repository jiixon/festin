package com.festin.app.waiting.application.port.out;

import com.festin.app.waiting.domain.model.Waiting;
import com.festin.app.waiting.domain.model.WaitingStatus;

import java.util.List;
import java.util.Optional;

/**
 * Waiting 영구 저장소 Port
 *
 * 책임:
 * - Waiting 데이터의 영구 저장/조회
 * - 호출(CALLED) 시점부터 MySQL에 저장
 * - 사용자의 활성 대기 목록 조회
 *
 * 구현체:
 * - JpaWaitingAdapter (MySQL 기반)
 */
public interface WaitingRepositoryPort {

    /**
     * Waiting 저장
     *
     * @param waiting 저장할 Waiting
     * @return 저장된 Waiting (ID 포함)
     */
    Waiting save(Waiting waiting);

    /**
     * ID로 Waiting 조회
     *
     * @param waitingId Waiting ID
     * @return Waiting 정보
     */
    Optional<Waiting> findById(Long waitingId);

    /**
     * 사용자의 활성 대기 목록 조회 (CALLED 상태만)
     *
     * @param userId 사용자 ID
     * @return 활성 대기 목록 (CALLED 상태)
     */
    List<Waiting> findActiveWaitingsByUserId(Long userId);

}
