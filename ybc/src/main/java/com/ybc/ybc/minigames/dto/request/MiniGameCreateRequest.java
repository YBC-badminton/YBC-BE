package com.ybc.ybc.minigames.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MiniGameCreateRequest(
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

        @NotNull(message = "타겟 스코어는 필수입니다.")
        Integer targetScore
) {
}
