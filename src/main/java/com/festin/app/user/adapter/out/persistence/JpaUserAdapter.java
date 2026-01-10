package com.festin.app.user.adapter.out.persistence;

import com.festin.app.user.adapter.out.persistence.entity.UserEntity;
import com.festin.app.user.adapter.out.persistence.repository.UserJpaRepository;
import com.festin.app.user.application.port.out.UserRepositoryPort;
import com.festin.app.user.domain.model.User;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * User 영구 저장소 JPA Adapter
 */
@Component
public class JpaUserAdapter implements UserRepositoryPort {

    private final UserJpaRepository userJpaRepository;

    public JpaUserAdapter(UserJpaRepository userJpaRepository) {
        this.userJpaRepository = userJpaRepository;
    }

    @Override
    public Optional<User> findById(Long userId) {
        return userJpaRepository.findById(userId)
                .map(this::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userJpaRepository.findByEmail(email)
                .map(this::toDomain);
    }

    @Override
    public User save(User user) {
        UserEntity entity;

        if (user.getId() == null) {
            // 신규 사용자 생성
            if (user.getManagedBoothId() != null) {
                entity = new UserEntity(user.getEmail(), user.getNickname(), user.getRole(), user.getManagedBoothId());
            } else {
                entity = new UserEntity(user.getEmail(), user.getNickname(), user.getRole());
            }
        } else {
            // 기존 사용자 업데이트
            entity = userJpaRepository.findById(user.getId())
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + user.getId()));
            entity.updateNickname(user.getNickname());
            if (user.getFcmToken() != null) {
                entity.updateFcmToken(user.getFcmToken());
            }
            if (user.getManagedBoothId() != null) {
                entity.updateManagedBoothId(user.getManagedBoothId());
            }
        }

        UserEntity savedEntity = userJpaRepository.save(entity);
        return toDomain(savedEntity);
    }

    private User toDomain(UserEntity entity) {
        return User.of(
                entity.getId(),
                entity.getEmail(),
                entity.getNickname(),
                entity.getRole(),
                entity.getFcmToken(),
                entity.getNotificationEnabled(),
                entity.getManagedBoothId()
        );
    }
}