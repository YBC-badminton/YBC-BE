package com.ybc.ybc.vote.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record AdminMatchGenerateCourtAssignmentRequest(
        @NotNull(message = "코트 번호를 입력해 주세요.")
        @Positive(message = "코트 번호는 1 이상이어야 합니다.")
        Integer courtNumber,
        @NotNull(message = "코트 참석자를 입력해 주세요.")
        List<@Valid AdminMatchParticipantRequest> participants
) {
}
