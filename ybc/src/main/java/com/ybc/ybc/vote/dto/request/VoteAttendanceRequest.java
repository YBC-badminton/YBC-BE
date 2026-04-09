package com.ybc.ybc.vote.dto.request;

import jakarta.validation.constraints.NotNull;

public record VoteAttendanceRequest(
        @NotNull(message = "참석 여부는 필수입니다.")
        Boolean attendanceStatus
) {
}
