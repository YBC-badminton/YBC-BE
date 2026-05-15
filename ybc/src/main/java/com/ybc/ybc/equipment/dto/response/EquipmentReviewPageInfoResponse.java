package com.ybc.ybc.equipment.dto.response;

import org.springframework.data.domain.Page;

public record EquipmentReviewPageInfoResponse(
        long totalElements,
        int totalPages,
        boolean isLast
) {
    public static EquipmentReviewPageInfoResponse from(Page<?> page) {
        return new EquipmentReviewPageInfoResponse(
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}
