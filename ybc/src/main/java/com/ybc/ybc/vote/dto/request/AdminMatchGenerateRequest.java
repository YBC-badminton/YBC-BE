package com.ybc.ybc.vote.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record AdminMatchGenerateRequest(
        @NotNull(message = "코트 수를 입력해 주세요.")
        @Positive(message = "코트 수는 1 이상이어야 합니다.")
        Integer courtCount,
        @NotEmpty(message = "코트 배정 정보를 입력해 주세요.")
        List<@Valid AdminMatchGenerateCourtAssignmentRequest> courtAssignments
) {
}
