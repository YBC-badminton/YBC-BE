package com.ybc.ybc.minigames.dto.response;

import java.util.List;

public record MiniGameListResponse(
        List<MiniGameSummaryResponse> games
) {
    public static MiniGameListResponse from(List<MiniGameSummaryResponse> games) {
        return new MiniGameListResponse(games);
    }
}
