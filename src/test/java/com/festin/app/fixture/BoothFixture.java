package com.festin.app.fixture;

import com.festin.app.booth.adapter.out.persistence.entity.BoothEntity;
import com.festin.app.booth.adapter.out.persistence.repository.BoothJpaRepository;
import com.festin.app.booth.domain.model.BoothStatus;
import com.festin.app.university.adapter.out.persistence.entity.UniversityEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Booth Fixture
 *
 * 테스트용 Booth 엔티티 생성 헬퍼
 *
 * 책임:
 * - Booth Entity 생성 및 저장
 * - Redis 메타 정보 자동 동기화
 * - currentPeople 초기화
 */
@Component
public class BoothFixture {

    @Autowired
    private BoothJpaRepository boothRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    /**
     * OPEN 상태의 부스 생성
     *
     * @param university 대학교 엔티티
     * @param name 부스 이름
     * @param capacity 정원
     * @return 생성된 부스 ID
     */
    public Long createOpenBooth(UniversityEntity university, String name, int capacity) {
        BoothEntity booth = new BoothEntity(
            university,
            name,
            name + " 설명",
            capacity,
            BoothStatus.OPEN
        );
        Long boothId = boothRepository.save(booth).getId();

        // Redis 메타 정보 동기화
        String metaKey = "booth:" + boothId + ":meta";
        redisTemplate.opsForHash().put(metaKey, "status", "OPEN");
        redisTemplate.opsForHash().put(metaKey, "name", name);
        redisTemplate.opsForHash().put(metaKey, "capacity", String.valueOf(capacity));

        // currentPeople 초기화
        String currentKey = "booth:" + boothId + ":current";
        redisTemplate.opsForValue().set(currentKey, "0");

        return boothId;
    }

    /**
     * 특정 상태의 부스 생성
     *
     * @param university 대학교 엔티티
     * @param name 부스 이름
     * @param capacity 정원
     * @param status 부스 상태
     * @return 생성된 부스 ID
     */
    public Long createBooth(UniversityEntity university, String name, int capacity, BoothStatus status) {
        BoothEntity booth = new BoothEntity(
            university,
            name,
            name + " 설명",
            capacity,
            status
        );
        Long boothId = boothRepository.save(booth).getId();

        // Redis 메타 정보 동기화
        String metaKey = "booth:" + boothId + ":meta";
        redisTemplate.opsForHash().put(metaKey, "status", status.name());
        redisTemplate.opsForHash().put(metaKey, "name", name);
        redisTemplate.opsForHash().put(metaKey, "capacity", String.valueOf(capacity));

        // currentPeople 초기화
        String currentKey = "booth:" + boothId + ":current";
        redisTemplate.opsForValue().set(currentKey, "0");

        return boothId;
    }
}
