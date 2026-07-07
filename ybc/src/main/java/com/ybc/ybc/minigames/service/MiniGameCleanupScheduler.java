package com.ybc.ybc.minigames.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.minigames.cleanup",
        name = "scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class MiniGameCleanupScheduler {
    private final GameService gameService;

    @Scheduled(
            cron = "${app.minigames.cleanup.cron:0 5 0 * * *}",
            zone = "${app.minigames.cleanup.zone:Asia/Seoul}"
    )
    public void deleteExpiredMiniGames() {
        gameService.deleteExpiredMiniGames();
    }
}
