package com.ybc.ybc.minigames.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MiniGameFinishedUpdateRequest(
        @NotBlank(message = "경기 제목은 필수입니다.")
        String title,

        @NotBlank(message = "팀1 멤버1 이름은 필수입니다.")
        String team1Member1,

        @NotBlank(message = "팀1 멤버2 이름은 필수입니다.")
        String team1Member2,

        @NotBlank(message = "팀2 멤버1 이름은 필수입니다.")
        String team2Member1,

        @NotBlank(message = "팀2 멤버2 이름은 필수입니다.")
        String team2Member2,

        @NotNull(message = "팀1 점수는 필수입니다.")
        @Min(value = 0, message = "팀1 점수는 0 이상이어야 합니다.")
        Integer team1Score,

        @NotNull(message = "팀2 점수는 필수입니다.")
        @Min(value = 0, message = "팀2 점수는 0 이상이어야 합니다.")
        Integer team2Score
) {
}
