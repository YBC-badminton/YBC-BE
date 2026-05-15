package com.ybc.ybc.vote.dto.request;

import com.ybc.ybc.vote.domain.MatchParticipantType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AdminMatchParticipantRequest(
        @NotNull(message = "참석자 유형을 입력해 주세요.")
        MatchParticipantType participantType,
        @NotNull(message = "참석자 식별자를 입력해 주세요.")
        @Positive(message = "참석자 식별자는 1 이상이어야 합니다.")
        Long participantId
) {
}
