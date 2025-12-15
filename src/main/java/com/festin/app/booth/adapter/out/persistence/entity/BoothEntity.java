package com.festin.app.booth.adapter.out.persistence.entity;

import com.festin.app.booth.domain.model.BoothStatus;
import com.festin.app.common.BaseTimeEntity;
import com.festin.app.university.adapter.out.persistence.entity.UniversityEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Entity
@Table(name = "booth")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BoothEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "university_id", nullable = false)
    private UniversityEntity university;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Integer capacity;

    @Column(name = "open_time")
    private LocalTime openTime;

    @Column(name = "close_time")
    private LocalTime closeTime;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private BoothStatus status = BoothStatus.CLOSED;

    public BoothEntity(UniversityEntity university, String name, String description,
                       Integer capacity, BoothStatus status) {
        this.university = university;
        this.name = name;
        this.description = description;
        this.capacity = capacity;
        this.status = status;
    }
}
