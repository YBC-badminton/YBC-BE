package com.ybc.ybc.minigames.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record MiniGameScoreUpdateRequest(
        @NotNull(message = "팀1 점수는 필수입니다.")
        @Min(value = 0, message = "팀1 점수는 0 이상이어야 합니다.")
        Integer team1Score,

        @NotNull(message = "팀2 점수는 필수입니다.")
        @Min(value = 0, message = "팀2 점수는 0 이상이어야 합니다.")
        Integer team2Score
) {
}
