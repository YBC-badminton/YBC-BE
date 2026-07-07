package com.ybc.ybc.minigames.dto.response;

import com.ybc.ybc.minigames.domain.MiniGame;
import com.ybc.ybc.minigames.domain.MiniGameWinnerTeam;

public record MiniGameFinishedUpdateResponse(
        Long gameId,
        String title,
        String team1Member1,
        String team1Member2,
        String team2Member1,
        String team2Member2,
        int team1Score,
        int team2Score,
        MiniGameWinnerTeam winnerTeam
) {
    public static MiniGameFinishedUpdateResponse from(MiniGame miniGame) {
        return new MiniGameFinishedUpdateResponse(
                miniGame.getId(),
                miniGame.getTitle(),
                miniGame.getTeam1Member1(),
                miniGame.getTeam1Member2(),
                miniGame.getTeam2Member1(),
                miniGame.getTeam2Member2(),
                miniGame.getTeam1Score(),
                miniGame.getTeam2Score(),
                miniGame.getWinnerTeam()
        );
    }
}
