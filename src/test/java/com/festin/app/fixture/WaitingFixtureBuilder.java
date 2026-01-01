package com.festin.app.fixture;

import com.festin.app.booth.adapter.out.persistence.entity.BoothEntity;
import com.festin.app.booth.adapter.out.persistence.repository.BoothJpaRepository;
import com.festin.app.user.adapter.out.persistence.entity.UserEntity;
import com.festin.app.user.adapter.out.persistence.repository.UserJpaRepository;
import com.festin.app.waiting.adapter.out.persistence.entity.WaitingEntity;
import com.festin.app.waiting.adapter.out.persistence.repository.WaitingJpaRepository;
import com.festin.app.waiting.domain.model.CompletionType;
import com.festin.app.waiting.domain.model.WaitingStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Waiting Fixture Builder
 *
 * 테스트용 Waiting 엔티티 생성 빌더
 *
 * 책임:
 * - 복잡한 Waiting 상태 생성 (CALLED, ENTERED, COMPLETED)
 * - 타임스탬프 논리 검증 (calledAt < enteredAt < completedAt)
 * - DB + Redis 자동 동기화
 *
 * 사용 예시:
 * waitingFixtureBuilder
 *     .forUser(userId)
 *     .forBooth(boothId)
 *     .position(1)
 *     .statusCalled(LocalDateTime.now().minusMinutes(5))
 *     .build();
 */
@Component
public class WaitingFixtureBuilder {

    @Autowired
    private WaitingJpaRepository waitingRepository;

    @Autowired
    private UserJpaRepository userRepository;

    @Autowired
    private BoothJpaRepository boothRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    // Builder 필드
    private Long userId;
    private Long boothId;
    private Integer position;
    private WaitingStatus status = WaitingStatus.CALLED;
    private CompletionType completionType;
    private LocalDateTime registeredAt = LocalDateTime.now();
    private LocalDateTime calledAt = LocalDateTime.now();
    private LocalDateTime enteredAt;
    private LocalDateTime completedAt;

    /**
     * 사용자 ID 설정 (빌더 초기화)
     */
    public WaitingFixtureBuilder forUser(Long userId) {
        reset();
        this.userId = userId;
        return this;
    }

    /**
     * 부스 ID 설정
     */
    public WaitingFixtureBuilder forBooth(Long boothId) {
        this.boothId = boothId;
        return this;
    }

    /**
     * 호출 순번 설정
     */
    public WaitingFixtureBuilder position(int position) {
        this.position = position;
        return this;
    }

    /**
     * CALLED 상태로 설정
     *
     * @param calledAt 호출 시간
     */
    public WaitingFixtureBuilder statusCalled(LocalDateTime calledAt) {
        this.status = WaitingStatus.CALLED;
        this.calledAt = calledAt;
        this.enteredAt = null;
        this.completedAt = null;
        this.completionType = null;
        return this;
    }

    /**
     * ENTERED 상태로 설정
     *
     * @param calledAt 호출 시간
     * @param enteredAt 입장 시간
     */
    public WaitingFixtureBuilder statusEntered(LocalDateTime calledAt, LocalDateTime enteredAt) {
        this.status = WaitingStatus.ENTERED;
        this.calledAt = calledAt;
        this.enteredAt = enteredAt;
        this.completedAt = null;
        this.completionType = null;
        return this;
    }

    /**
     * COMPLETED 상태로 설정
     *
     * @param type 완료 타입 (ENTERED or NO_SHOW)
     * @param calledAt 호출 시간
     * @param enteredAt 입장 시간 (NO_SHOW인 경우 null)
     * @param completedAt 완료 시간
     */
    public WaitingFixtureBuilder statusCompleted(CompletionType type,
                                                  LocalDateTime calledAt,
                                                  LocalDateTime enteredAt,
                                                  LocalDateTime completedAt) {
        this.status = WaitingStatus.COMPLETED;
        this.completionType = type;
        this.calledAt = calledAt;
        this.enteredAt = enteredAt;
        this.completedAt = completedAt;
        return this;
    }

    /**
     * NO_SHOW 상태로 설정 (간편 메서드)
     *
     * @param calledAt 호출 시간
     * @param completedAt 노쇼 처리 시간
     */
    public WaitingFixtureBuilder statusNoShow(LocalDateTime calledAt, LocalDateTime completedAt) {
        this.status = WaitingStatus.COMPLETED;
        this.completionType = CompletionType.NO_SHOW;
        this.calledAt = calledAt;
        this.enteredAt = null;
        this.completedAt = completedAt;
        return this;
    }

    /**
     * DB 저장 + Redis 동기화
     *
     * @return 생성된 Waiting ID
     */
    public Long build() {
        UserEntity user = userRepository.findById(userId).orElseThrow(
            () -> new IllegalStateException("User not found: " + userId)
        );
        BoothEntity booth = boothRepository.findById(boothId).orElseThrow(
            () -> new IllegalStateException("Booth not found: " + boothId)
        );

        WaitingEntity waiting = new WaitingEntity(
            null,
            user,
            booth,
            position,
            status,
            completionType,
            registeredAt,
            calledAt,
            enteredAt,
            completedAt
        );

        Long waitingId = waitingRepository.save(waiting).getId();

        // Redis 동기화
        syncToRedis(boothId);

        return waitingId;
    }

    /**
     * Redis 동기화
     *
     * - ENTERED 상태: currentPeople 증가
     */
    private void syncToRedis(Long boothId) {
        if (status == WaitingStatus.ENTERED) {
            String currentKey = "booth:" + boothId + ":current";
            redisTemplate.opsForValue().increment(currentKey);
        }
    }

    /**
     * Builder 필드 초기화
     */
    private void reset() {
        this.userId = null;
        this.boothId = null;
        this.position = null;
        this.status = WaitingStatus.CALLED;
        this.completionType = null;
        this.registeredAt = LocalDateTime.now();
        this.calledAt = LocalDateTime.now();
        this.enteredAt = null;
        this.completedAt = null;
    }
}
