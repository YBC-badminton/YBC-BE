package com.ybc.ybc.equipment.dto.response;

import com.ybc.ybc.equipment.domain.EquipmentReview;
import java.time.LocalDateTime;

public record EquipmentReviewCreateResponse(
        Long reviewId,
        String category,
        String brandName,
        String productName,
        int usageMonth,
        int rating,
        String content,
        String writer,
        LocalDateTime createdAt
) {
    public static EquipmentReviewCreateResponse from(EquipmentReview review) {
        return new EquipmentReviewCreateResponse(
                review.getId(),
                review.getEquipment().getCategory().name(),
                review.getEquipment().getBrand().getName(),
                review.getEquipment().getName(),
                review.getUsageMonth(),
                review.getRating(),
                review.getContent(),
                review.getMember().getNickname(),
                review.getCreatedAt()
        );
    }
}
