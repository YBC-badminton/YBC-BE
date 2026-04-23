package com.ybc.ybc.equipment.dto.request;

import com.ybc.ybc.equipment.domain.EquipmentCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EquipmentReviewCreateRequest(
        @NotNull(message = "장비 카테고리는 필수입니다.")
        EquipmentCategory category,

        @NotBlank(message = "브랜드명은 필수입니다.")
        String brandName,

        @NotBlank(message = "상품명은 필수입니다.")
        String productName,

        @NotNull(message = "사용 개월 수는 필수입니다.")
        @Min(value = 1, message = "사용 개월 수는 1 이상이어야 합니다.")
        Integer usageMonth,

        @NotNull(message = "평점은 필수입니다.")
        @Min(value = 1, message = "평점은 1 이상이어야 합니다.")
        @Max(value = 5, message = "평점은 5 이하여야 합니다.")
        Long rating,

        @NotBlank(message = "후기 내용은 필수입니다.")
        String content
) {
}
