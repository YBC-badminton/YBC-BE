package com.ybc.ybc.recruitments.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record RecruitmentUpdateRequest(
        @NotBlank(message = "기수를 입력해 주세요.")
        String term,
        @NotNull(message = "모집 시작일을 입력해 주세요.")
        LocalDateTime startAt,
        @NotNull(message = "모집 마감일을 입력해 주세요.")
        LocalDateTime endAt
) {
}
