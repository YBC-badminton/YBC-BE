package com.ybc.ybc.minigames.dto.response;

import com.ybc.ybc.minigames.domain.MiniGame;
import com.ybc.ybc.minigames.domain.MiniGameStatus;

public record MiniGameCreateResponse(
        Long gameId,
        String title,
        String team1Member1,
        String team1Member2,
        String team2Member1,
        String team2Member2,
        int targetScore,
        int team1Score,
        int team2Score,
        MiniGameStatus status
) {
    public static MiniGameCreateResponse from(MiniGame miniGame) {
        return new MiniGameCreateResponse(
                miniGame.getId(),
                miniGame.getTitle(),
                miniGame.getTeam1Member1(),
                miniGame.getTeam1Member2(),
                miniGame.getTeam2Member1(),
                miniGame.getTeam2Member2(),
                miniGame.getTargetScore(),
                miniGame.getTeam1Score(),
                miniGame.getTeam2Score(),
                miniGame.getStatus()
        );
    }
}
