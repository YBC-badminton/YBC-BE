package com.ybc.ybc.equipment.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EquipmentReviewUpdateRequest(
        @NotNull(message = "사용 개월 수는 필수입니다.")
        @Min(value = 1, message = "사용 개월 수는 1 이상이어야 합니다.")
        Integer usageMonth,

        @NotNull(message = "평점은 필수입니다.")
        @Min(value = 1, message = "평점은 1 이상이어야 합니다.")
        @Max(value = 5, message = "평점은 5 이하여야 합니다.")
        Integer rating,

        @NotBlank(message = "후기 내용은 필수입니다.")
        String content
) {
}
