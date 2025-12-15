package com.festin.booth.domain.model;

import com.festin.booth.domain.BoothClosedException;
import com.festin.booth.domain.BoothFullException;

import java.time.LocalTime;

/**
 * Booth (부스) - 도메인 모델
 *
 * 책임:
 * - 부스 운영 상태 관리
 * - 수용 인원(정원) 관리
 * - 운영 시간 정보 제공
 *
 * 운영 시간 vs 운영 상태:
 * - openTime/closeTime: 매일 고정된 운영 시간 (참고용, DB 저장)
 * - status: 스태프가 수동으로 제어하는 실시간 상태 (Redis 저장)
 */
public class Booth {

    private final Long id;
    private final Long universityId;
    private final String name;
    private final String description;
    private final Integer capacity;        // 최대 수용 인원
    private final LocalTime openTime;      // 운영 시작 시간 (매일 고정)
    private final LocalTime closeTime;     // 운영 종료 시간 (매일 고정)

    private BoothStatus status;            // 실시간 운영 상태 (스태프가 수동 제어)

    private Booth(Builder builder) {
        this.id = builder.id;
        this.universityId = builder.universityId;
        this.name = builder.name;
        this.description = builder.description;
        this.capacity = builder.capacity;
        this.openTime = builder.openTime;
        this.closeTime = builder.closeTime;
        this.status = builder.status;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 부스 오픈 (스태프가 수동으로 호출)
     * Redis에 상태 저장 필요
     */
    public void open() {
        this.status = BoothStatus.OPEN;
    }

    /**
     * 부스 마감 (스태프가 수동으로 호출)
     * Redis에 상태 저장 필요
     */
    public void close() {
        this.status = BoothStatus.CLOSED;
    }

    /**
     * 운영 중 여부 확인
     */
    public boolean isOpen() {
        return this.status == BoothStatus.OPEN;
    }

    /**
     * 운영 중인지 검증 (대기 등록 시 사용)
     * Redis에서 실시간 상태 확인
     */
    public void validateOpen() {
        if (!isOpen()) {
            throw new BoothClosedException();
        }
    }

    /**
     * 정원 여유 검증 (호출 시 사용)
     *
     * @param currentCount 현재 입장 인원
     */
    public void validateCapacity(int currentCount) {
        if (currentCount >= capacity) {
            throw new BoothFullException();
        }
    }

    // Getters
    public Long getId() {
        return id;
    }

    public Long getUniversityId() {
        return universityId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public LocalTime getOpenTime() {
        return openTime;
    }

    public LocalTime getCloseTime() {
        return closeTime;
    }

    public BoothStatus getStatus() {
        return status;
    }

    public static class Builder {
        private Long id;
        private Long universityId;
        private String name;
        private String description;
        private Integer capacity;
        private LocalTime openTime;
        private LocalTime closeTime;
        private BoothStatus status = BoothStatus.CLOSED;

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder universityId(Long universityId) {
            this.universityId = universityId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder capacity(Integer capacity) {
            this.capacity = capacity;
            return this;
        }

        public Builder openTime(LocalTime openTime) {
            this.openTime = openTime;
            return this;
        }

        public Builder closeTime(LocalTime closeTime) {
            this.closeTime = closeTime;
            return this;
        }

        public Builder status(BoothStatus status) {
            this.status = status;
            return this;
        }

        public Booth build() {
            return new Booth(this);
        }
    }
}