package com.ybc.ybc.applications.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ApplicationStatusUpdateRequest(
        @NotBlank(message = "지원 상태를 입력해 주세요.")
        String status
) {
}
