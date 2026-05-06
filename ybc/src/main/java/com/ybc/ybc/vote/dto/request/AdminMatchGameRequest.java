package com.ybc.ybc.vote.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AdminMatchGameRequest(
        @NotNull(message = "게임 번호를 입력해 주세요.")
        @Positive(message = "게임 번호는 1 이상이어야 합니다.")
        Integer matchNumber,
        @NotNull(message = "1팀 참석자를 입력해 주세요.")
        @Size(min = 2, max = 2, message = "복식 팀은 2명으로 구성해 주세요.")
        List<@NotNull(message = "참석자 정보를 입력해 주세요.") @Valid AdminMatchParticipantRequest> team1,
        @NotNull(message = "2팀 참석자를 입력해 주세요.")
        @Size(min = 2, max = 2, message = "복식 팀은 2명으로 구성해 주세요.")
        List<@NotNull(message = "참석자 정보를 입력해 주세요.") @Valid AdminMatchParticipantRequest> team2
) {
}
