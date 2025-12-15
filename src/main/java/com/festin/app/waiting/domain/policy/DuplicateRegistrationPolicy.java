package com.festin.waiting.domain.policy;

import com.festin.waiting.domain.exception.AlreadyRegisteredException;
import org.springframework.stereotype.Component;

/**
 * 중복 등록 방지 정책
 *
 * 비즈니스 규칙:
 * - 같은 부스에 중복 등록 불가
 * - 이미 대기 중인 부스에 재등록 시도 시 예외 발생
 */
@Component
public class DuplicateRegistrationPolicy {

    /**
     * 중복 등록 검증
     *
     * @param isAlreadyRegistered 이미 등록되어 있는지 여부
     * @throws AlreadyRegisteredException 이미 등록된 경우
     */
    public void validate(boolean isAlreadyRegistered) {
        if (isAlreadyRegistered) {
            throw new AlreadyRegisteredException();
        }
    }
}
