package com.festin.app.booth.adapter.in.web;

import com.festin.app.booth.adapter.in.web.dto.BoothListResponse;
import com.festin.app.booth.adapter.in.web.dto.CompleteResponse;
import com.festin.app.booth.adapter.in.web.dto.EntranceResponse;
import com.festin.app.booth.application.port.in.GetBoothListUseCase;
import com.festin.app.booth.application.port.in.dto.BoothListResult;
import com.festin.app.waiting.application.port.in.CompleteExperienceUseCase;
import com.festin.app.waiting.application.port.in.ConfirmEntranceUseCase;
import com.festin.app.waiting.application.port.in.result.CompleteResult;
import com.festin.app.waiting.application.port.in.result.EntranceResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 부스 관리 Controller
 *
 * API 스펙:
 * - GET /api/v1/booths - 부스 목록 조회
 * - POST /api/v1/booths/{boothId}/entrance/{waitingId} - 입장 확인 (스태프 전용)
 * - POST /api/v1/booths/{boothId}/complete/{waitingId} - 체험 완료 (스태프 전용)
 */
@RestController
@RequestMapping("/api/v1/booths")
@RequiredArgsConstructor
public class BoothController {

    private final GetBoothListUseCase getBoothListUseCase;
    private final ConfirmEntranceUseCase confirmEntranceUseCase;
    private final CompleteExperienceUseCase completeExperienceUseCase;

    /**
     * 부스 목록 조회
     *
     * GET /api/v1/booths?universityId={universityId}
     *
     * @param universityId 대학 ID (Query Parameter, optional)
     * @return 200 OK - 부스 목록
     */
    @GetMapping
    public ResponseEntity<BoothListResponse> getBoothList(
            @RequestParam(required = false) Long universityId
    ) {
        BoothListResult result = getBoothListUseCase.getBoothList(universityId);
        return ResponseEntity.ok(BoothListResponse.from(result));
    }

    /**
     * 입장 확인 (스태프 전용)
     *
     * POST /api/v1/booths/{boothId}/entrance/{waitingId}
     *
     * @param boothId 부스 ID (Path Variable)
     * @param waitingId 대기 ID (Path Variable)
     * @return 200 OK - 입장 확인 결과 (waitingId, status, enteredAt)
     */
    @PostMapping("/{boothId}/entrance/{waitingId}")
    public ResponseEntity<EntranceResponse> confirmEntrance(
            @PathVariable Long boothId,
            @PathVariable Long waitingId
    ) {
        EntranceResult result = confirmEntranceUseCase.confirmEntrance(boothId, waitingId);

        return ResponseEntity.ok(EntranceResponse.from(result));
    }

    /**
     * 체험 완료 (스태프 전용)
     *
     * POST /api/v1/booths/{boothId}/complete/{waitingId}
     *
     * @param boothId 부스 ID (Path Variable)
     * @param waitingId 대기 ID (Path Variable)
     * @return 200 OK - 체험 완료 결과 (waitingId, status, completionType, completedAt)
     */
    @PostMapping("/{boothId}/complete/{waitingId}")
    public ResponseEntity<CompleteResponse> completeExperience(
            @PathVariable Long boothId,
            @PathVariable Long waitingId
    ) {
        CompleteResult result = completeExperienceUseCase.complete(boothId, waitingId);

        return ResponseEntity.ok(CompleteResponse.from(result));
    }
}