package com.ybc.ybc.minigames.dto.request;

import com.ybc.ybc.minigames.domain.MiniGameWinnerTeam;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record MiniGameEndRequest(
        @NotNull(message = "경기 날짜는 필수입니다.")
        LocalDate gameDate,

        @NotNull(message = "경기 시작 시간은 필수입니다.")
        LocalTime startTime,

        @NotNull(message = "경기 종료 시간은 필수입니다.")
        LocalTime endTime,

        @NotNull(message = "팀1 점수는 필수입니다.")
        @Min(value = 0, message = "팀1 점수는 0 이상이어야 합니다.")
        Integer team1Score,

        @NotNull(message = "팀2 점수는 필수입니다.")
        @Min(value = 0, message = "팀2 점수는 0 이상이어야 합니다.")
        Integer team2Score,

        @NotNull(message = "우승팀은 필수입니다.")
        MiniGameWinnerTeam winnerTeam
) {
}
