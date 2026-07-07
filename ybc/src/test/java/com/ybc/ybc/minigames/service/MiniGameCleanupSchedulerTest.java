package com.ybc.ybc.minigames.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MiniGameCleanupSchedulerTest {
    @Mock
    private GameService gameService;

    @InjectMocks
    private MiniGameCleanupScheduler miniGameCleanupScheduler;

    @Test
    @DisplayName("미니 게임 정리 스케줄러는 만료된 게임 삭제를 위임한다")
    void deleteExpiredMiniGamesDelegatesToService() {
        miniGameCleanupScheduler.deleteExpiredMiniGames();

        verify(gameService).deleteExpiredMiniGames();
    }
}
