package com.ybc.ybc.interviews.dto.request;

import com.ybc.ybc.applications.domain.InterviewTime;
import jakarta.validation.constraints.NotBlank;

public record InterviewUpdateRequest(
        InterviewTime scheduledAt,
        @NotBlank(message = "면접 상태를 입력해 주세요.")
        String status,
        String memo
) {
}
