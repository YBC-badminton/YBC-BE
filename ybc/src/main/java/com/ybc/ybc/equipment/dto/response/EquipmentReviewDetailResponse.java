package com.ybc.ybc.equipment.dto.response;

import com.ybc.ybc.equipment.domain.EquipmentReview;
import java.time.LocalDateTime;

public record EquipmentReviewDetailResponse(
        Long reviewId,
        String category,
        String brandName,
        String equipmentName,
        int rating,
        int usageMonth,
        String content,
        Long memberId,
        String writer,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean isAuthor
) {
    public static EquipmentReviewDetailResponse from(EquipmentReview review, Long currentMemberId) {
        return new EquipmentReviewDetailResponse(
                review.getId(),
                review.getEquipment().getCategory().name(),
                review.getEquipment().getBrand().getName(),
                review.getEquipment().getName(),
                review.getRating(),
                review.getUsageMonth(),
                review.getContent(),
                review.getMember().getId(),
                review.getMember().getNickname(),
                review.getCreatedAt(),
                review.getUpdatedAt(),
                review.getMember().getId().equals(currentMemberId)
        );
    }
}
