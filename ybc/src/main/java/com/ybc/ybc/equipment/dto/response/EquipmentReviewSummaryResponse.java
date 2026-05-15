package com.ybc.ybc.equipment.dto.response;

import com.ybc.ybc.equipment.domain.EquipmentReview;

import java.time.LocalDateTime;

public record EquipmentReviewSummaryResponse(
        Long reviewId,
        String category,
        String brandName,
        String productName,
        int rating,
        int usageMonth,
        String content,
        String memberNickname,
        LocalDateTime createdAt
) {
    public static EquipmentReviewSummaryResponse from(EquipmentReview review) {
        return new EquipmentReviewSummaryResponse(
                review.getId(),
                review.getEquipment().getCategory().name(),
                review.getEquipment().getBrand().getName(),
                review.getEquipment().getName(),
                review.getRating(),
                review.getUsageMonth(),
                review.getContent(),
                review.getMember().getNickname(),
                review.getCreatedAt()
        );
    }
}
