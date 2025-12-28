package com.festin.app.university.domain.model;

/**
 * University (대학) - 도메인 모델
 *
 * 책임:
 * - 대학 정보 관리
 */
public class University {

    private final Long id;
    private final String name;
    private final String domain;  // 이메일 도메인 (예: snu.ac.kr)

    private University(Long id, String name, String domain) {
        this.id = id;
        this.name = name;
        this.domain = domain;
    }

    /**
     * University 생성 (정적 팩토리 메서드)
     *
     * @param id University ID
     * @param name 대학 이름
     * @param domain 이메일 도메인 (예: snu.ac.kr)
     * @return University 도메인 객체
     */
    public static University of(Long id, String name, String domain) {
        return new University(id, name, domain);
    }

    // Getters
    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDomain() {
        return domain;
    }
}