package com.festin.app.booth.application.port.out;

import com.festin.app.booth.domain.model.Booth;
import com.festin.app.booth.domain.model.BoothStatus;

import java.util.Optional;

/**
 * 부스 상태 캐시 Port
 *
 * 책임:
 * - 부스 현재 인원 실시간 관리 (Redis)
 * - 부스 운영 상태 관리 (OPEN/CLOSED)
 * - 부스 정원 관리
 * - 부스 기본 정보 캐싱 (이름 등)
 *
 * 구현체:
 * - RedisBoothAdapter
 *
 * Redis 키 형식:
 * - booth:{boothId}:current (String) - 현재 인원 (원자적 증감을 위해 별도 관리)
 * - booth:{boothId}:meta (HASH) - 부스 메타 정보
 *   - name: 부스 이름
 *   - capacity: 최대 정원
 *   - status: 운영 상태 (OPEN/CLOSED)
 */
public interface BoothCachePort {

    /**
     * 부스 도메인 객체 조회
     *
     * Redis 캐시에서 부스 정보를 조회하여 Booth 도메인 객체로 반환
     *
     * @param boothId 부스 ID
     * @return Booth 도메인 객체 (캐시에 없으면 Empty)
     */
    Optional<Booth> getBooth(Long boothId);

    /**
     * 부스 현재 인원 증가
     *
     * @param boothId 부스 ID
     * @return 증가 후 현재 인원
     */
    int incrementCurrentCount(Long boothId);

    /**
     * 부스 현재 인원 감소
     *
     * @param boothId 부스 ID
     * @return 감소 후 현재 인원
     */
    int decrementCurrentCount(Long boothId);

    /**
     * 부스 현재 인원 조회
     *
     * @param boothId 부스 ID
     * @return 현재 인원
     */
    int getCurrentCount(Long boothId);

    /**
     * 부스 운영 상태 저장
     *
     * @param boothId 부스 ID
     * @param status 운영 상태
     */
    void setStatus(Long boothId, BoothStatus status);

    /**
     * 부스 운영 상태 조회
     *
     * @param boothId 부스 ID
     * @return 운영 상태
     */
    Optional<BoothStatus> getStatus(Long boothId);

    /**
     * 부스 정원 저장
     *
     * @param boothId 부스 ID
     * @param capacity 최대 정원
     */
    void setCapacity(Long boothId, int capacity);

    /**
     * 부스 정원 조회
     *
     * @param boothId 부스 ID
     * @return 최대 정원
     */
    Optional<Integer> getCapacity(Long boothId);

    /**
     * 부스 이름 저장
     *
     * @param boothId 부스 ID
     * @param name 부스 이름
     */
    void setName(Long boothId, String name);

    /**
     * 부스 이름 조회
     *
     * @param boothId 부스 ID
     * @return 부스 이름
     */
    Optional<String> getName(Long boothId);

    /**
     * 부스 설명 저장
     *
     * @param boothId 부스 ID
     * @param description 부스 설명
     */
    void setDescription(Long boothId, String description);

    /**
     * 부스 설명 조회
     *
     * @param boothId 부스 ID
     * @return 부스 설명
     */
    Optional<String> getDescription(Long boothId);

    /**
     * 부스 대학 이름 저장
     *
     * @param boothId 부스 ID
     * @param universityName 대학 이름
     */
    void setUniversityName(Long boothId, String universityName);

    /**
     * 부스 대학 이름 조회
     *
     * @param boothId 부스 ID
     * @return 대학 이름
     */
    Optional<String> getUniversityName(Long boothId);

    /**
     * 모든 부스 정보 일괄 조회 (Pipeline)
     *
     * Redis Pipeline을 사용하여 네트워크 왕복을 최소화
     *
     * @param boothIds 부스 ID 목록
     * @return 부스별 메타 정보 Map
     */
    java.util.Map<Long, BoothMeta> getBoothMetas(java.util.List<Long> boothIds);

    /**
     * 부스 메타 정보
     */
    record BoothMeta(
            Long boothId,
            String name,
            String description,
            String universityName,
            BoothStatus status,
            int capacity
    ) {}

    /**
     * 모든 부스 ID 목록 조회
     *
     * @return 부스 ID 목록
     */
    java.util.List<Long> getAllBoothIds();

    /**
     * 부스 ID 목록에 부스 추가
     *
     * @param boothId 부스 ID
     */
    void addBoothId(Long boothId);
}
