package com.festin.app.waiting.adapter.in.web;

import com.festin.app.waiting.adapter.in.web.dto.CallNextRequest;
import com.festin.app.waiting.adapter.in.web.dto.CallNextResponse;
import com.festin.app.waiting.adapter.in.web.dto.EnqueueRequest;
import com.festin.app.waiting.adapter.in.web.dto.EnqueueResponse;
import com.festin.app.waiting.adapter.in.web.dto.PositionResponse;
import com.festin.app.waiting.application.port.in.CallNextUseCase;
import com.festin.app.waiting.application.port.in.CancelWaitingUseCase;
import com.festin.app.waiting.application.port.in.EnqueueUseCase;
import com.festin.app.waiting.application.port.in.GetPositionUseCase;
import com.festin.app.waiting.application.port.in.command.EnqueueCommand;
import com.festin.app.waiting.application.port.in.result.CallResult;
import com.festin.app.waiting.application.port.in.result.EnqueueResult;
import com.festin.app.waiting.application.port.in.result.PositionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 대기 관리 Controller
 *
 * API 스펙:
 * - POST /api/v1/waitings - 대기 등록
 * - GET /api/v1/waitings/booth/{boothId} - 순번 조회
 * - DELETE /api/v1/waitings/{boothId} - 대기 취소
 * - POST /api/v1/waitings/call - 다음 사람 호출 (스태프 전용)
 */
@RestController
@RequestMapping("/api/v1/waitings")
@RequiredArgsConstructor
public class WaitingController {

    private final EnqueueUseCase enqueueUseCase;
    private final GetPositionUseCase getPositionUseCase;
    private final CancelWaitingUseCase cancelWaitingUseCase;
    private final CallNextUseCase callNextUseCase;

    /**
     * 대기 등록
     *
     * POST /api/v1/waitings
     *
     * @param userId 사용자 ID (임시로 헤더로 받음, 추후 JWT 인증으로 대체)
     * @param request 대기 등록 요청
     * @return 201 Created - 대기 등록 결과
     */
    @PostMapping
    public ResponseEntity<EnqueueResponse> enqueue(
        @RequestHeader("X-User-Id") Long userId,
        @RequestBody EnqueueRequest request
    ) {
        EnqueueCommand command = new EnqueueCommand(userId, request.getBoothId());
        EnqueueResult result = enqueueUseCase.enqueue(command);

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(EnqueueResponse.from(result));
    }

    /**
     * 순번 조회
     *
     * GET /api/v1/waitings/booth/{boothId}
     *
     * @param userId 사용자 ID (임시로 헤더로 받음, 추후 JWT 인증으로 대체)
     * @param boothId 부스 ID (Path Variable)
     * @return 200 OK - 순번 조회 결과
     */
    @GetMapping("/booth/{boothId}")
    public ResponseEntity<PositionResponse> getPosition(
        @RequestHeader("X-User-Id") Long userId,
        @PathVariable Long boothId
    ) {
        PositionResult result = getPositionUseCase.getPosition(userId, boothId);

        return ResponseEntity.ok(PositionResponse.from(result));
    }

    /**
     * 대기 취소
     *
     * DELETE /api/v1/waitings/{boothId}
     *
     * @param userId 사용자 ID (임시로 헤더로 받음, 추후 JWT 인증으로 대체)
     * @param boothId 부스 ID (Path Variable)
     * @return 204 No Content
     */
    @DeleteMapping("/{boothId}")
    public ResponseEntity<Void> cancelWaiting(
        @RequestHeader("X-User-Id") Long userId,
        @PathVariable Long boothId
    ) {
        cancelWaitingUseCase.cancel(userId, boothId);

        return ResponseEntity.noContent().build();
    }

    /**
     * 다음 사람 호출 (스태프 전용)
     *
     * POST /api/v1/waitings/call
     *
     * @param request 호출 요청 (boothId 포함)
     * @return 200 OK - 호출 결과 (waitingId, userId, position, calledAt)
     */
    @PostMapping("/call")
    public ResponseEntity<CallNextResponse> callNext(
        @RequestBody CallNextRequest request
    ) {
        CallResult result = callNextUseCase.callNext(request.getBoothId());

        return ResponseEntity.ok(CallNextResponse.from(result));
    }
}