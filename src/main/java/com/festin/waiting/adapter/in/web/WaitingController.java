package com.festin.app.adapter.in.web;

import com.festin.app.adapter.in.web.dto.EnqueueRequest;
import com.festin.app.adapter.in.web.dto.EnqueueResponse;
import com.festin.app.adapter.in.web.dto.PositionResponse;
import com.festin.app.application.port.in.EnqueueUseCase;
import com.festin.app.application.port.in.GetPositionUseCase;
import com.festin.app.application.port.in.command.EnqueueCommand;
import com.festin.app.application.port.in.result.EnqueueResult;
import com.festin.app.application.port.in.result.PositionResult;
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
 */
@RestController
@RequestMapping("/api/v1/waitings")
@RequiredArgsConstructor
public class WaitingController {

    private final EnqueueUseCase enqueueUseCase;
    private final GetPositionUseCase getPositionUseCase;

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
}