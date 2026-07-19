package com.ybc.ybc.minigames.dto.response;

import com.ybc.ybc.minigames.domain.MiniGame;
import com.ybc.ybc.minigames.domain.MiniGameWinnerTeam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record MiniGameSummaryResponse(
        Long gameId,
        String title,
        String team1Member1,
        String team1Member2,
        String team2Member1,
        String team2Member2,
        int team1Score,
        int team2Score,
        MiniGameWinnerTeam winnerTeam,
        LocalDateTime startTime,
        LocalDateTime endTime
) {
    public static MiniGameSummaryResponse from(MiniGame miniGame) {
        return new MiniGameSummaryResponse(
                miniGame.getId(),
                miniGame.getTitle(),
                miniGame.getTeam1Member1(),
                miniGame.getTeam1Member2(),
                miniGame.getTeam2Member1(),
                miniGame.getTeam2Member2(),
                miniGame.getTeam1Score(),
                miniGame.getTeam2Score(),
                miniGame.getWinnerTeam(),
                combine(miniGame.getGameDate(), miniGame.getStartTime()),
                combine(miniGame.getGameDate(), miniGame.getEndTime())
        );
    }

    private static LocalDateTime combine(LocalDate gameDate, LocalTime time) {
        if (gameDate == null || time == null) {
            return null;
        }
        return gameDate.atTime(time);
    }
}
