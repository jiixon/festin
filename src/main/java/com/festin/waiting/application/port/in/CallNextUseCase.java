package com.festin.waiting.application.port.in;

import com.festin.waiting.application.port.in.result.CallResult;

/**
 * 다음 사람 호출 UseCase
 *
 * 비즈니스 요구사항:
 * - 스태프가 "다음 사람 호출" 버튼 클릭
 * - 대기열 순서대로 1명씩 자동 선택
 * - 부스 정원에 여유가 있어야 호출 가능
 * - 호출 시점부터 영구 저장소(MySQL)에 저장
 * - Kafka를 통한 푸시 알림 발송
 *
 * 처리 흐름:
 * 1. 부스 정원 검증
 * 2. Redis 대기열에서 1명 dequeue
 * 3. MySQL에 Waiting 저장 (상태: CALLED)
 * 4. Kafka로 호출 알림 발행
 * 5. 부스 현재 인원 +1
 */
public interface CallNextUseCase {

    /**
     * 다음 대기자 호출
     *
     * @param boothId 부스 ID
     * @return 호출 결과
     * @throws com.festin.waiting.domain.exception.BoothFullException 정원 초과
     * @throws com.festin.waiting.domain.exception.QueueEmptyException 대기열 비어있음
     */
    CallResult callNext(Long boothId);
}