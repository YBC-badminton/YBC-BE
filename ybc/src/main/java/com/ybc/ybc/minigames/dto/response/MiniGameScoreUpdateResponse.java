package com.ybc.ybc.minigames.dto.response;

import com.ybc.ybc.minigames.domain.MiniGame;

public record MiniGameScoreUpdateResponse(
        Long gameId,
        int team1Score,
        int team2Score
) {
    public static MiniGameScoreUpdateResponse from(MiniGame miniGame) {
        return new MiniGameScoreUpdateResponse(
                miniGame.getId(),
                miniGame.getTeam1Score(),
                miniGame.getTeam2Score()
        );
    }
}
