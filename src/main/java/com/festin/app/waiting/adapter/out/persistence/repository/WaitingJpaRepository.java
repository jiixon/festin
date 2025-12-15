package com.festin.app.waiting.adapter.out.persistence.repository;

import com.festin.app.waiting.adapter.out.persistence.entity.WaitingEntity;
import com.festin.app.waiting.domain.model.WaitingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Waiting JPA Repository
 *
 * Spring Data JPA 인터페이스
 */
public interface WaitingJpaRepository extends JpaRepository<WaitingEntity, Long> {

    /**
     * 사용자의 활성 대기 건수 조회
     *
     * @param userId 사용자 ID
     * @param statuses 활성 상태 목록
     * @return 활성 대기 건수
     */
    @Query("SELECT COUNT(w) FROM WaitingEntity w WHERE w.user.id = :userId AND w.status IN :statuses")
    int countByUserIdAndStatusIn(@Param("userId") Long userId, @Param("statuses") List<WaitingStatus> statuses);
}
