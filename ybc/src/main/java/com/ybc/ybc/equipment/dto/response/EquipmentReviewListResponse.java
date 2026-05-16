package com.ybc.ybc.equipment.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

public record EquipmentReviewListResponse(
        List<EquipmentReviewSummaryResponse> reviews,
        EquipmentReviewPageInfoResponse pageInfo
) {
    public static EquipmentReviewListResponse from(Page<EquipmentReviewSummaryResponse> page) {
        return new EquipmentReviewListResponse(
                page.getContent(),
                EquipmentReviewPageInfoResponse.from(page)
        );
    }
}
