package com.ybc.ybc.equipment.domain;

import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.global.exception.ExceptionCode;
import com.ybc.ybc.global.exception.YbcException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "equipment_review")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EquipmentReview {
    private static final long MIN_RATING = 1L;
    private static final long MAX_RATING = 5L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "equipment_review_id")
    private Long id;

    @Column(nullable = false)
    private Long rating;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "usage_period", nullable = false, length = 50)
    private String usagePeriod;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    @Builder
    private EquipmentReview(Long rating,
                            String content,
                            String usagePeriod,
                            Member member,
                            Equipment equipment) {
        validateRating(rating);
        this.rating = rating;
        this.content = content;
        this.usagePeriod = usagePeriod;
        this.member = member;
        this.equipment = equipment;
    }

    public static EquipmentReview create(Long rating,
                                         String content,
                                         String usagePeriod,
                                         Member member,
                                         Equipment equipment) {
        return EquipmentReview.builder()
                .rating(rating)
                .content(content)
                .usagePeriod(usagePeriod)
                .member(member)
                .equipment(equipment)
                .build();
    }

    public void update(Long rating, String content, String usagePeriod) {
        validateRating(rating);
        this.rating = rating;
        this.content = content;
        this.usagePeriod = usagePeriod;
    }

    private void validateRating(Long rating) {
        if (rating == null || rating < MIN_RATING || rating > MAX_RATING) {
            throw new YbcException(ExceptionCode.INVALID_EQUIPMENT_REVIEW_RATING);
        }
    }
}
