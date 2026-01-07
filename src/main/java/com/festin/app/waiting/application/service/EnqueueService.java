package com.festin.app.waiting.application.service;

import com.festin.app.waiting.application.port.in.EnqueueUseCase;
import com.festin.app.waiting.application.port.in.command.EnqueueCommand;
import com.festin.app.waiting.application.port.in.result.EnqueueResult;
import com.festin.app.booth.application.port.out.BoothCachePort;
import com.festin.app.booth.domain.BoothNotFoundException;
import com.festin.app.booth.domain.model.Booth;
import com.festin.app.waiting.application.port.out.QueueCachePort;
import com.festin.app.waiting.domain.model.EnqueueResultFactory;
import com.festin.app.waiting.domain.policy.MaxWaitingPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 대기 등록 UseCase 구현
 *
 * 비즈니스 흐름 (멱등성 보장 + Race Condition 방지):
 * 1. 부스 운영 상태 검증 (OPEN 여부)
 * 2. Lua Script로 원자적 등록 처리
 *    - 중복 등록 체크 (멱등성)
 *    - 최대 대기 수 검증 (2개 제한)
 *    - Redis 대기열에 추가
 *    - 활성 부스 목록에 추가
 * 3. 결과에 따라 적절한 응답 반환
 */
@Service
@RequiredArgsConstructor
public class EnqueueService implements EnqueueUseCase {

    private final QueueCachePort queueCachePort;
    private final BoothCachePort boothCachePort;

    private final MaxWaitingPolicy maxWaitingPolicy;

    @Override
    @Transactional
    public EnqueueResult enqueue(EnqueueCommand command) {
        Booth booth = boothCachePort.getBooth(command.boothId()).orElseThrow(BoothNotFoundException::new);
        booth.validateForEnqueue();

        LocalDateTime now = LocalDateTime.now();
        QueueCachePort.EnqueueAtomicResult result = queueCachePort.enqueueAtomic(
                command.boothId(),
                command.userId(),
                now,
                maxWaitingPolicy.getMaxWaitingBooths()
        );

        return EnqueueResultFactory.from(result, booth, now, queueCachePort, command.boothId(), command.userId());
    }
}