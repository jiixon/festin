package com.festin.university.domain.model;

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

    private University(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.domain = builder.domain;
    }

    public static Builder builder() {
        return new Builder();
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

    public static class Builder {
        private Long id;
        private String name;
        private String domain;

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder domain(String domain) {
            this.domain = domain;
            return this;
        }

        public University build() {
            return new University(this);
        }
    }
}