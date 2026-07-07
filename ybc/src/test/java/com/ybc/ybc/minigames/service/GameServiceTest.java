package com.ybc.ybc.minigames.service;

import com.ybc.ybc.minigames.domain.MiniGame;
import com.ybc.ybc.minigames.domain.MiniGameStatus;
import com.ybc.ybc.minigames.dto.request.MiniGameCreateRequest;
import com.ybc.ybc.minigames.dto.request.MiniGameScoreUpdateRequest;
import com.ybc.ybc.minigames.dto.response.MiniGameCreateResponse;
import com.ybc.ybc.minigames.dto.response.MiniGameScoreUpdateResponse;
import com.ybc.ybc.minigames.repository.MiniGameRepository;
import com.ybc.ybc.global.exception.YbcException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameServiceTest {
    @Mock
    private MiniGameRepository miniGameRepository;

    @InjectMocks
    private GameService gameService;

    @Test
    @DisplayName("미니 게임 생성 시 초기 스코어와 상태를 저장한다")
    void createMiniGameSavesInitialState() {
        MiniGameCreateRequest request = new MiniGameCreateRequest(
                "7월 1주차 미니게임",
                "김민수",
                "이서연",
                "박지훈",
                "최유진",
                21
        );
        when(miniGameRepository.save(any(MiniGame.class))).thenAnswer(invocation -> {
            MiniGame miniGame = invocation.getArgument(0);
            ReflectionTestUtils.setField(miniGame, "id", 1L);
            return miniGame;
        });

        MiniGameCreateResponse response = gameService.createMiniGame(request);

        ArgumentCaptor<MiniGame> captor = ArgumentCaptor.forClass(MiniGame.class);
        verify(miniGameRepository).save(captor.capture());
        MiniGame savedMiniGame = captor.getValue();
        assertThat(savedMiniGame.getTitle()).isEqualTo("7월 1주차 미니게임");
        assertThat(savedMiniGame.getTargetScore()).isEqualTo(21);
        assertThat(savedMiniGame.getTeam1Score()).isEqualTo(0);
        assertThat(savedMiniGame.getTeam2Score()).isEqualTo(0);
        assertThat(savedMiniGame.getStatus()).isEqualTo(MiniGameStatus.IN_PROGRESS);
        assertThat(response.gameId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(MiniGameStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("허용되지 않은 타겟 스코어면 미니 게임 생성 시 예외가 발생한다")
    void createMiniGameThrowsWhenTargetScoreIsInvalid() {
        MiniGameCreateRequest request = new MiniGameCreateRequest(
                "7월 1주차 미니게임",
                "김민수",
                "이서연",
                "박지훈",
                "최유진",
                17
        );

        assertThatThrownBy(() -> gameService.createMiniGame(request))
                .isInstanceOf(YbcException.class)
                .hasMessage("타겟 스코어는 11점, 21점, 25점만 가능합니다.");
        verifyNoInteractions(miniGameRepository);
    }

    @Test
    @DisplayName("진행 중인 미니 게임 스코어를 수정한다")
    void updateMiniGameScoreUpdatesScores() {
        MiniGame miniGame = MiniGame.create(
                "7월 1주차 미니게임",
                "김민수",
                "이서연",
                "박지훈",
                "최유진",
                21
        );
        ReflectionTestUtils.setField(miniGame, "id", 1L);
        when(miniGameRepository.findById(1L)).thenReturn(Optional.of(miniGame));

        MiniGameScoreUpdateResponse response = gameService.updateMiniGameScore(
                1L,
                new MiniGameScoreUpdateRequest(10, 8)
        );

        assertThat(miniGame.getTeam1Score()).isEqualTo(10);
        assertThat(miniGame.getTeam2Score()).isEqualTo(8);
        assertThat(response.gameId()).isEqualTo(1L);
        assertThat(response.team1Score()).isEqualTo(10);
        assertThat(response.team2Score()).isEqualTo(8);
    }

    @Test
    @DisplayName("존재하지 않는 미니 게임은 스코어를 수정할 수 없다")
    void updateMiniGameScoreThrowsWhenMiniGameIsMissing() {
        when(miniGameRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gameService.updateMiniGameScore(999L, new MiniGameScoreUpdateRequest(10, 8)))
                .isInstanceOf(YbcException.class)
                .hasMessage("존재하지 않는 미니 게임입니다.");
    }

    @Test
    @DisplayName("타겟 스코어를 넘는 점수는 저장할 수 없다")
    void updateMiniGameScoreThrowsWhenScoreExceedsTargetScore() {
        MiniGame miniGame = MiniGame.create(
                "7월 1주차 미니게임",
                "김민수",
                "이서연",
                "박지훈",
                "최유진",
                21
        );
        when(miniGameRepository.findById(1L)).thenReturn(Optional.of(miniGame));

        assertThatThrownBy(() -> gameService.updateMiniGameScore(1L, new MiniGameScoreUpdateRequest(22, 8)))
                .isInstanceOf(YbcException.class)
                .hasMessage("팀 점수는 0점 이상 타겟 스코어 이하여야 합니다.");
    }
}
