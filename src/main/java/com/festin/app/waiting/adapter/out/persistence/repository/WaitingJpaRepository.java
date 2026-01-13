package com.festin.app.waiting.adapter.out.persistence.repository;

import com.festin.app.waiting.adapter.out.persistence.entity.WaitingEntity;
import com.festin.app.waiting.domain.model.WaitingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
        * @param userId   사용자 ID
        * @param statuses 활성 상태 목록
        * @return 활성 대기 건수
        */
       @Query("SELECT COUNT(w) FROM WaitingEntity w WHERE w.user.id = :userId AND w.status IN :statuses")
       int countByUserIdAndStatusIn(@Param("userId") Long userId, @Param("statuses") List<WaitingStatus> statuses);

       /**
        * 사용자의 CALLED 상태 대기 목록 조회
        *
        * @param userId 사용자 ID
        * @param status 상태 (CALLED)
        * @return 대기 목록
        */
       List<WaitingEntity> findByUserIdAndStatus(Long userId, WaitingStatus status);

       /**
        * 오늘 호출된 총 인원 (부스별)
        *
        * @param boothId 부스 ID
        * @param date    조회 날짜
        * @return 호출된 총 인원
        */
       @Query("SELECT COUNT(w) FROM WaitingEntity w " +
                     "WHERE w.booth.id = :boothId " +
                     "AND DATE(w.calledAt) = :date")
       int countTodayCalledByBoothId(@Param("boothId") Long boothId, @Param("date") LocalDate date);

       /**
        * 오늘 입장한 총 인원 (부스별, ENTERED 상태)
        *
        * @param boothId 부스 ID
        * @param date    조회 날짜
        * @return 입장한 총 인원
        */
       @Query("SELECT COUNT(w) FROM WaitingEntity w " +
                     "WHERE w.booth.id = :boothId " +
                     "AND w.status = 'ENTERED' " +
                     "AND DATE(w.calledAt) = :date")
       int countTodayEnteredByBoothId(@Param("boothId") Long boothId, @Param("date") LocalDate date);

       /**
        * 오늘 노쇼 총 인원 (부스별, COMPLETED + NO_SHOW)
        *
        * @param boothId 부스 ID
        * @param date    조회 날짜
        * @return 노쇼 총 인원
        */
       @Query("SELECT COUNT(w) FROM WaitingEntity w " +
                     "WHERE w.booth.id = :boothId " +
                     "AND w.status = 'COMPLETED' " +
                     "AND w.completionType = 'NO_SHOW' " +
                     "AND DATE(w.calledAt) = :date")
       int countTodayNoShowByBoothId(@Param("boothId") Long boothId, @Param("date") LocalDate date);

       /**
        * 오늘 정상 완료 총 인원 (부스별, COMPLETED + ENTERED)
        *
        * @param boothId 부스 ID
        * @param date    조회 날짜
        * @return 정상 완료 총 인원
        */
       @Query("SELECT COUNT(w) FROM WaitingEntity w " +
                     "WHERE w.booth.id = :boothId " +
                     "AND w.status = 'COMPLETED' " +
                     "AND w.completionType = 'ENTERED' " +
                     "AND DATE(w.calledAt) = :date")
       int countTodayCompletedByBoothId(@Param("boothId") Long boothId, @Param("date") LocalDate date);

       /**
        * 부스의 호출된 대기 목록 조회 (User 정보 포함)
        *
        * JOIN FETCH로 User 엔티티를 즉시 로딩하여 N+1 쿼리 방지
        *
        * @param boothId 부스 ID
        * @param status  상태 (CALLED)
        * @return 호출된 대기 목록 (User JOIN FETCH, calledAt 순 정렬)
        */
       @Query("SELECT w FROM WaitingEntity w " +
                     "JOIN FETCH w.user " +
                     "WHERE w.booth.id = :boothId " +
                     "AND w.status = :status " +
                     "ORDER BY w.calledAt ASC")
       List<WaitingEntity> findByBoothIdAndStatusWithUser(
                     @Param("boothId") Long boothId,
                     @Param("status") WaitingStatus status);

       /**
        * 부스의 활성 대기 목록 조회 (CALLED + ENTERED, User 정보 포함)
        *
        * JOIN FETCH로 User 엔티티를 즉시 로딩하여 N+1 쿼리 방지
        *
        * @param boothId  부스 ID
        * @param statuses 상태 목록 (CALLED, ENTERED)
        * @return 활성 대기 목록 (User JOIN FETCH, calledAt 순 정렬)
        */
       @Query("SELECT w FROM WaitingEntity w " +
                     "JOIN FETCH w.user " +
                     "WHERE w.booth.id = :boothId " +
                     "AND w.status IN :statuses " +
                     "ORDER BY w.calledAt ASC")
       List<WaitingEntity> findByBoothIdAndStatusesWithUser(
                     @Param("boothId") Long boothId,
                     @Param("statuses") List<WaitingStatus> statuses);

       /**
        * 타임아웃된 대기 조회 (노쇼 처리 대상)
        *
        * @param status           상태 (CALLED)
        * @param timeoutThreshold 타임아웃 기준 시각
        * @return 타임아웃된 대기 목록
        */
       List<WaitingEntity> findByStatusAndCalledAtBefore(WaitingStatus status, LocalDateTime timeoutThreshold);

       /**
        * 최근 특정 상태의 대기 목록 조회 (배치 보정용)
        *
        * TransactionalEventListener 방식 배치에서 사용
        *
        * @param status 상태 (CALLED)
        * @param since  조회 시작 시각
        * @return 최근 해당 상태의 대기 목록
        */
       List<WaitingEntity> findByStatusAndCalledAtAfter(WaitingStatus status, LocalDateTime since);

       /**
        * 특정 사용자/부스/상태 존재 여부 확인 (배치 보정용)
        *
        * Soft Lock 방식 배치에서 사용
        *
        * @param userId  사용자 ID
        * @param boothId 부스 ID
        * @param status  상태 (CALLED)
        * @return 존재 여부
        */
       boolean existsByUserIdAndBoothIdAndStatus(Long userId, Long boothId, WaitingStatus status);
}
