package com.festin.app.cucumber;

import com.festin.app.booth.adapter.in.web.dto.BoothDetailResponse;
import com.festin.app.booth.adapter.in.web.dto.BoothListResponse;
import com.festin.app.booth.adapter.in.web.dto.BoothStatusResponse;
import com.festin.app.user.adapter.in.web.dto.UpdateFcmTokenResponse;
import com.festin.app.waiting.adapter.in.web.dto.CalledListResponse;
import com.festin.app.waiting.adapter.in.web.dto.MyWaitingListResponse;
import io.cucumber.spring.ScenarioScope;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Cucumber 시나리오 간 상태를 공유하는 컨텍스트
 *
 * @ScenarioScope: 각 시나리오마다 새로운 인스턴스 생성
 */
@Component
@ScenarioScope
public class TestContext {

    // Entity ID 저장소
    private final Map<String, Long> userMap = new HashMap<>();
    private final Map<String, Long> universityMap = new HashMap<>();
    private final Map<String, Long> boothMap = new HashMap<>();

    // API 응답 저장소
    private BoothListResponse boothListResponse;
    private BoothDetailResponse boothDetailResponse;
    private BoothStatusResponse boothStatusResponse;
    private CalledListResponse calledListResponse;
    private MyWaitingListResponse myWaitingListResponse;
    private UpdateFcmTokenResponse updateFcmTokenResponse;

    // Map Getters (캡슐화를 유지하면서 Map 접근 허용)
    public Map<String, Long> getUserMap() {
        return userMap;
    }

    public Map<String, Long> getUniversityMap() {
        return universityMap;
    }

    public Map<String, Long> getBoothMap() {
        return boothMap;
    }

    // Booth List Response
    public void setBoothListResponse(BoothListResponse response) {
        this.boothListResponse = response;
    }

    public BoothListResponse getBoothListResponse() {
        return boothListResponse;
    }

    // Booth Detail Response
    public void setBoothDetailResponse(BoothDetailResponse response) {
        this.boothDetailResponse = response;
    }

    public BoothDetailResponse getBoothDetailResponse() {
        return boothDetailResponse;
    }

    // Booth Status Response
    public void setBoothStatusResponse(BoothStatusResponse response) {
        this.boothStatusResponse = response;
    }

    public BoothStatusResponse getBoothStatusResponse() {
        return boothStatusResponse;
    }

    // Called List Response
    public void setCalledListResponse(CalledListResponse response) {
        this.calledListResponse = response;
    }

    public CalledListResponse getCalledListResponse() {
        return calledListResponse;
    }

    // My Waiting List Response
    public void setMyWaitingListResponse(MyWaitingListResponse response) {
        this.myWaitingListResponse = response;
    }

    public MyWaitingListResponse getMyWaitingListResponse() {
        return myWaitingListResponse;
    }

    // Update FCM Token Response
    public void setUpdateFcmTokenResponse(UpdateFcmTokenResponse response) {
        this.updateFcmTokenResponse = response;
    }

    public UpdateFcmTokenResponse getUpdateFcmTokenResponse() {
        return updateFcmTokenResponse;
    }

    // Clear All
    public void clearAll() {
        userMap.clear();
        universityMap.clear();
        boothMap.clear();
        boothListResponse = null;
        boothDetailResponse = null;
        boothStatusResponse = null;
        calledListResponse = null;
        myWaitingListResponse = null;
        updateFcmTokenResponse = null;
    }
}