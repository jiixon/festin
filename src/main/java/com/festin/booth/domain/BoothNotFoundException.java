package com.festin.booth.domain;

import com.festin.common.exception.DomainException;
import com.festin.common.exception.ErrorCode;

/**
 * 부스를 찾을 수 없는 경우 발생하는 예외
 *
 * 발생 상황:
 * - 존재하지 않는 부스 ID로 조회 시도
 * - 삭제된 부스 접근 시도
 */
public class BoothNotFoundException extends DomainException {

    private BoothNotFoundException(Long boothId) {
        super(ErrorCode.BOOTH_NOT_FOUND, "부스를 찾을 수 없습니다. (ID: " + boothId + ")");
    }

    public static BoothNotFoundException of(Long boothId) {
        return new BoothNotFoundException(boothId);
    }
}