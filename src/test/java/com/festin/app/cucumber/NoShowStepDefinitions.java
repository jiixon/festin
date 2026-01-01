package com.festin.app.cucumber;

import com.festin.app.waiting.adapter.out.persistence.entity.WaitingEntity;
import com.festin.app.waiting.adapter.out.persistence.repository.WaitingJpaRepository;
import com.festin.app.waiting.application.service.NoShowSchedulerService;
import com.festin.app.waiting.domain.model.CompletionType;
import com.festin.app.waiting.domain.model.WaitingStatus;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 노쇼 자동 처리 Step Definitions
 */
public class NoShowStepDefinitions {

    @Autowired
    private WaitingJpaRepository waitingJpaRepository;

    @Autowired
    private NoShowSchedulerService noShowSchedulerService;

    @Autowired
    private EntityManager entityManager;

    @And("5분이 경과했다")
    @Transactional
    public void fiveMinutesHavePassed() {
        // MySQL에서 호출된 Waiting의 calledAt을 6분 전으로 직접 업데이트
        WaitingEntity waiting = waitingJpaRepository.findAll().get(0);
        Long waitingId = waiting.getId();

        // Native query로 직접 업데이트
        entityManager.createNativeQuery(
                "UPDATE waiting SET called_at = :calledAt WHERE id = :id"
        )
        .setParameter("calledAt", LocalDateTime.now().minusMinutes(6))
        .setParameter("id", waitingId)
        .executeUpdate();

        entityManager.flush();
        entityManager.clear();
    }

    @When("노쇼 자동 처리가 실행된다")
    public void noShowProcessingIsExecuted() {
        // 노쇼 스케줄러 직접 호출
        noShowSchedulerService.processNoShow();
    }

    @Then("사용자의 대기 상태는 {string}가 된다")
    public void userWaitingStatusBecomes(String expectedStatus) {
        entityManager.clear(); // 캐시 초기화
        WaitingEntity waiting = waitingJpaRepository.findAll().get(0);
        assertThat(waiting.getStatus()).isEqualTo(WaitingStatus.valueOf(expectedStatus));
    }

    @And("사용자의 완료 유형은 {string}가 된다")
    public void userCompletionTypeBecomes(String expectedType) {
        entityManager.clear(); // 캐시 초기화
        WaitingEntity waiting = waitingJpaRepository.findAll().get(0);
        assertThat(waiting.getCompletionType()).isEqualTo(CompletionType.valueOf(expectedType));
    }
}