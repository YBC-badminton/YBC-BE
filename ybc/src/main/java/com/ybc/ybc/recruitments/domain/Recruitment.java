package com.ybc.ybc.recruitments.domain;

import com.ybc.ybc.global.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "recruitment")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recruitment extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recruitment_id")
    private Long id;

    @Column(nullable = false)
    private String term;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    @Column(nullable = false)
    private LocalDate firstResultDate;

    @Column(nullable = false)
    private LocalDate finalResultDate;

    @Column(nullable = false)
    private LocalDate interviewDate;

    @Column(nullable = false)
    private String interviewLocation;

    @Column(nullable = false)
    private String interviewFirstTime;

    @Column(nullable = false)
    private String interviewSecondTime;

    @Column
    private LocalDate otDate;

    @Column(nullable = false)
    private int membershipFee;

    @Column(nullable = false)
    private int activityPeriod;

    @Builder
    private Recruitment(String term,
                        LocalDateTime startAt,
                        LocalDateTime endAt,
                        LocalDate firstResultDate,
                        LocalDate finalResultDate,
                        LocalDate interviewDate,
                        String interviewLocation,
                        String interviewFirstTime,
                        String interviewSecondTime,
                        LocalDate otDate,
                        int membershipFee,
                        int activityPeriod) {
        this.term = term;
        this.startAt = startAt;
        this.endAt = endAt;
        this.firstResultDate = firstResultDate;
        this.finalResultDate = finalResultDate;
        this.interviewDate = interviewDate;
        this.interviewLocation = interviewLocation;
        this.interviewFirstTime = interviewFirstTime;
        this.interviewSecondTime = interviewSecondTime;
        this.otDate = otDate;
        this.membershipFee = membershipFee;
        this.activityPeriod = activityPeriod;
    }
}
