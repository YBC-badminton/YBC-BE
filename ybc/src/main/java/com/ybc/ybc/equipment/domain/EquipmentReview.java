package com.ybc.ybc.equipment.domain;

import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.global.domain.BaseEntity;
import com.ybc.ybc.global.exception.ExceptionCode;
import com.ybc.ybc.global.exception.YbcException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Getter
@Entity
@Table(name = "equipment_review")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EquipmentReview extends BaseEntity {
    private static final long MIN_RATING = 1L;
    private static final long MAX_RATING = 5L;
    private static final int MIN_USAGE_MONTH = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "equipment_review_id")
    private Long id;

    @Column(nullable = false)
    private int rating;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "usage_period", nullable = false)
    private int usageMonth;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    @Builder
    private EquipmentReview(int rating,
                            String content,
                            int usageMonth,
                            Member member,
                            Equipment equipment) {
        validateRating(rating);
        validateUsageMonth(usageMonth);
        this.rating = rating;
        this.content = content;
        this.usageMonth = usageMonth;
        this.member = member;
        this.equipment = equipment;
    }

    public static EquipmentReview create(int rating,
                                         String content,
                                         Integer usageMonth,
                                         Member member,
                                         Equipment equipment) {
        return EquipmentReview.builder()
                .rating(rating)
                .content(content)
                .usageMonth(usageMonth)
                .member(member)
                .equipment(equipment)
                .build();
    }

    public void update(int rating, String content, int usageMonth) {
        validateRating(rating);
        validateUsageMonth(usageMonth);
        this.rating = rating;
        this.content = content;
        this.usageMonth = usageMonth;
    }

    private void validateRating(int rating) {
        if (rating < MIN_RATING || rating > MAX_RATING) {
            throw new YbcException(ExceptionCode.INVALID_EQUIPMENT_REVIEW_RATING);
        }
    }

    private void validateUsageMonth(Integer usageMonth) {
        if (usageMonth == null || usageMonth < MIN_USAGE_MONTH) {
            throw new YbcException(ExceptionCode.INVALID_EQUIPMENT_USAGE_MONTH);
        }
    }
}
