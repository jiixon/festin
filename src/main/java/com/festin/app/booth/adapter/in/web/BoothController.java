package com.festin.app.booth.adapter.in.web;

import com.festin.app.booth.adapter.in.web.dto.BoothDetailResponse;
import com.festin.app.booth.adapter.in.web.dto.BoothListResponse;
import com.festin.app.booth.adapter.in.web.dto.BoothStatusResponse;
import com.festin.app.booth.adapter.in.web.dto.CompleteResponse;
import com.festin.app.booth.adapter.in.web.dto.EntranceResponse;
import com.festin.app.booth.application.port.in.GetBoothDetailUseCase;
import com.festin.app.booth.application.port.in.GetBoothListUseCase;
import com.festin.app.booth.application.port.in.GetBoothStatusUseCase;
import com.festin.app.booth.application.port.in.dto.BoothDetailResult;
import com.festin.app.booth.application.port.in.dto.BoothListResult;
import com.festin.app.booth.application.port.in.dto.BoothStatusResult;
import com.festin.app.waiting.adapter.in.web.dto.CalledListResponse;
import com.festin.app.waiting.application.port.in.CompleteExperienceUseCase;
import com.festin.app.waiting.application.port.in.ConfirmEntranceUseCase;
import com.festin.app.waiting.application.port.in.GetCalledListUseCase;
import com.festin.app.waiting.application.port.in.result.CalledListResult;
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
 * - GET /api/v1/booths/{boothId} - 부스 상세 조회
 * - GET /api/v1/booths/{boothId}/status - 부스 현황 조회 (스태프 전용)
 * - GET /api/v1/booths/{boothId}/called-list - 호출 대기 목록 조회 (스태프 전용)
 * - POST /api/v1/booths/{boothId}/entrance/{waitingId} - 입장 확인 (스태프 전용)
 * - POST /api/v1/booths/{boothId}/complete/{waitingId} - 체험 완료 (스태프 전용)
 */
@RestController
@RequestMapping("/api/v1/booths")
@RequiredArgsConstructor
public class BoothController {

    private final GetBoothListUseCase getBoothListUseCase;
    private final GetBoothDetailUseCase getBoothDetailUseCase;
    private final GetBoothStatusUseCase getBoothStatusUseCase;
    private final GetCalledListUseCase getCalledListUseCase;
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
     * 부스 상세 조회
     *
     * GET /api/v1/booths/{boothId}
     *
     * @param boothId 부스 ID (Path Variable)
     * @return 200 OK - 부스 상세 정보
     */
    @GetMapping("/{boothId}")
    public ResponseEntity<BoothDetailResponse> getBoothDetail(
            @PathVariable Long boothId
    ) {
        BoothDetailResult result = getBoothDetailUseCase.getBoothDetail(boothId);
        return ResponseEntity.ok(BoothDetailResponse.from(result));
    }

    /**
     * 부스 현황 조회 (스태프용)
     *
     * GET /api/v1/booths/{boothId}/status
     *
     * @param boothId 부스 ID (Path Variable)
     * @return 200 OK - 부스 현황 (현재 인원, 대기 인원, 오늘 통계)
     */
    @GetMapping("/{boothId}/status")
    public ResponseEntity<BoothStatusResponse> getBoothStatus(
            @PathVariable Long boothId
    ) {
        BoothStatusResult result = getBoothStatusUseCase.getBoothStatus(boothId);
        return ResponseEntity.ok(BoothStatusResponse.from(result));
    }

    /**
     * 호출 대기 목록 조회 (스태프용)
     *
     * GET /api/v1/booths/{boothId}/called-list
     *
     * @param boothId 부스 ID (Path Variable)
     * @return 200 OK - 호출된 대기 목록 (사용자 정보, 남은 시간 포함)
     */
    @GetMapping("/{boothId}/called-list")
    public ResponseEntity<CalledListResponse> getCalledList(
            @PathVariable Long boothId
    ) {
        CalledListResult result = getCalledListUseCase.getCalledList(boothId);
        return ResponseEntity.ok(CalledListResponse.from(result));
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