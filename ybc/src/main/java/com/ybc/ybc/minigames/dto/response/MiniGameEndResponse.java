package com.ybc.ybc.minigames.dto.response;

import com.ybc.ybc.minigames.domain.MiniGame;
import com.ybc.ybc.minigames.domain.MiniGameStatus;
import com.ybc.ybc.minigames.domain.MiniGameWinnerTeam;

public record MiniGameEndResponse(
        Long gameId,
        String title,
        int team1Score,
        int team2Score,
        MiniGameWinnerTeam winnerTeam,
        MiniGameStatus status
) {
    public static MiniGameEndResponse from(MiniGame miniGame) {
        return new MiniGameEndResponse(
                miniGame.getId(),
                miniGame.getTitle(),
                miniGame.getTeam1Score(),
                miniGame.getTeam2Score(),
                miniGame.getWinnerTeam(),
                miniGame.getStatus()
        );
    }
}
