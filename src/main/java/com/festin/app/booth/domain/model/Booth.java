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
    private final String name;
    private final int capacity;
    private final BoothStatus status;

    private Booth(Long id, String name, int capacity, BoothStatus status) {
        this.id = id;
        this.name = name;
        this.capacity = capacity;
        this.status = status;
    }

    public static Booth of(
            Long id,
            String name,
            int capacity,
            BoothStatus status
    ) {
        return new Booth(id, name, capacity, status);
    }

    /**
     * 대기 등록 가능 여부 검증
     */
    public void validateForEnqueue() {
        validateOpen();
    }

    /**
     * 다음 사람 호출 가능 여부 검증
     *
     * @param currentCount 현재 입장 인원
     */
    public void validateForCalling(int currentCount) {
        validateOpen();
        validateCapacity(currentCount);
    }

    private void validateOpen() {
        if (status != BoothStatus.OPEN) {
            throw new BoothClosedException();
        }
    }

    private void validateCapacity(int currentCount) {
        if (currentCount >= capacity) {
            throw new BoothFullException();
        }
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getCapacity() {
        return capacity;
    }

    public BoothStatus getStatus() {
        return status;
    }


}