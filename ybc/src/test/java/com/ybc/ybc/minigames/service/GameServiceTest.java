package com.ybc.ybc.minigames.service;

import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.domain.SocialProvider;
import com.ybc.ybc.auth.service.MemberService;
import com.ybc.ybc.global.exception.YbcException;
import com.ybc.ybc.minigames.domain.MiniGame;
import com.ybc.ybc.minigames.domain.MiniGameStatus;
import com.ybc.ybc.minigames.domain.MiniGameWinnerTeam;
import com.ybc.ybc.minigames.dto.request.MiniGameCreateRequest;
import com.ybc.ybc.minigames.dto.request.MiniGameEndRequest;
import com.ybc.ybc.minigames.dto.request.MiniGameFinishedUpdateRequest;
import com.ybc.ybc.minigames.dto.request.MiniGameScoreUpdateRequest;
import com.ybc.ybc.minigames.dto.response.MiniGameCreateResponse;
import com.ybc.ybc.minigames.dto.response.MiniGameEndResponse;
import com.ybc.ybc.minigames.dto.response.MiniGameFinishedUpdateResponse;
import com.ybc.ybc.minigames.dto.response.MiniGameListResponse;
import com.ybc.ybc.minigames.dto.response.MiniGameScoreUpdateResponse;
import com.ybc.ybc.minigames.repository.MiniGameRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameServiceTest {
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-14T03:00:00Z"), KOREA);

    @Mock
    private MiniGameRepository miniGameRepository;

    @Mock
    private MemberService memberService;

    @Mock
    private Clock clock;

    @InjectMocks
    private GameService gameService;

    @BeforeEach
    void setUpClock() {
        lenient().when(clock.instant()).thenReturn(FIXED_CLOCK.instant());
        lenient().when(clock.getZone()).thenReturn(FIXED_CLOCK.getZone());
    }

    @Test
    @DisplayName("미니 게임 생성 시 로그인 회원을 작성자로 저장한다")
    void createMiniGameSavesInitialState() {
        Member member = createMember(1L, "3001", "김민수");
        MiniGameCreateRequest request = new MiniGameCreateRequest(
                "7월 1주차 미니게임",
                "김민수",
                "이서연",
                "박지훈",
                "최유진",
                21
        );
        when(memberService.getMemberById(1L)).thenReturn(member);
        when(miniGameRepository.save(any(MiniGame.class))).thenAnswer(invocation -> {
            MiniGame miniGame = invocation.getArgument(0);
            ReflectionTestUtils.setField(miniGame, "id", 1L);
            return miniGame;
        });

        MiniGameCreateResponse response = gameService.createMiniGame(1L, request);

        ArgumentCaptor<MiniGame> captor = ArgumentCaptor.forClass(MiniGame.class);
        verify(miniGameRepository).save(captor.capture());
        MiniGame savedMiniGame = captor.getValue();
        assertThat(savedMiniGame.getTitle()).isEqualTo("7월 1주차 미니게임");
        assertThat(savedMiniGame.getTargetScore()).isEqualTo(21);
        assertThat(savedMiniGame.getTeam1Score()).isEqualTo(0);
        assertThat(savedMiniGame.getTeam2Score()).isEqualTo(0);
        assertThat(savedMiniGame.getMember().getId()).isEqualTo(1L);
        assertThat(savedMiniGame.getStatus()).isEqualTo(MiniGameStatus.IN_PROGRESS);
        assertThat(response.gameId()).isEqualTo(1L);
        assertThat(response.memberId()).isEqualTo(1L);
        assertThat(response.writer()).isEqualTo("김민수");
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

        assertThatThrownBy(() -> gameService.createMiniGame(1L, request))
                .isInstanceOf(YbcException.class)
                .hasMessage("타겟 스코어는 11점, 21점, 25점만 가능합니다.");
        verifyNoInteractions(miniGameRepository);
    }

    @Test
    @DisplayName("미니 게임 조회 시 종료된 경기 기록을 최신 순으로 반환한다")
    void getGamesReturnsFinishedGames() {
        MiniGame latestGame = MiniGame.create(
                "7월 첫째 주 미니게임",
                "김민수",
                "이서연",
                "박지훈",
                "최유진",
                25,
                createMember(1L, "3001", "김민수")
        );
        latestGame.finish(
                LocalDate.of(2026, 7, 19),
                LocalTime.of(14, 0),
                LocalTime.of(14, 25),
                25,
                23,
                MiniGameWinnerTeam.TEAM1
        );
        ReflectionTestUtils.setField(latestGame, "id", 1L);

        MiniGame previousGame = MiniGame.create(
                "초급 복식 경기",
                "정우성",
                "한지민",
                "김도현",
                "이수빈",
                21,
                createMember(2L, "3002", "정우성")
        );
        previousGame.finish(
                LocalDate.of(2026, 7, 18),
                LocalTime.of(15, 10),
                LocalTime.of(15, 35),
                18,
                21,
                MiniGameWinnerTeam.TEAM2
        );
        ReflectionTestUtils.setField(previousGame, "id", 2L);

        when(miniGameRepository.findAllByStatusOrderByGameDateDescStartTimeDescIdDesc(MiniGameStatus.FINISHED))
                .thenReturn(List.of(latestGame, previousGame));

        MiniGameListResponse response = gameService.getGames();

        assertThat(response.games()).hasSize(2);
        assertThat(response.games().get(0).gameId()).isEqualTo(1L);
        assertThat(response.games().get(0).memberId()).isEqualTo(1L);
        assertThat(response.games().get(0).title()).isEqualTo("7월 첫째 주 미니게임");
        assertThat(response.games().get(0).writer()).isEqualTo("김민수");
        assertThat(response.games().get(0).winnerTeam()).isEqualTo(MiniGameWinnerTeam.TEAM1);
        assertThat(response.games().get(0).startTime()).isEqualTo(LocalDateTime.of(2026, 7, 19, 14, 0));
        assertThat(response.games().get(0).endTime()).isEqualTo(LocalDateTime.of(2026, 7, 19, 14, 25));
        assertThat(response.games().get(1).gameId()).isEqualTo(2L);
        assertThat(response.games().get(1).memberId()).isEqualTo(2L);
        assertThat(response.games().get(1).writer()).isEqualTo("정우성");
        assertThat(response.games().get(1).winnerTeam()).isEqualTo(MiniGameWinnerTeam.TEAM2);
        verify(miniGameRepository).findAllByStatusOrderByGameDateDescStartTimeDescIdDesc(MiniGameStatus.FINISHED);
    }

    @Test
    @DisplayName("작성자는 진행 중인 미니 게임 스코어를 수정할 수 있다")
    void updateMiniGameScoreUpdatesScores() {
        MiniGame miniGame = MiniGame.create(
                "7월 1주차 미니게임",
                "김민수",
                "이서연",
                "박지훈",
                "최유진",
                21,
                createMember(1L, "3001", "김민수")
        );
        ReflectionTestUtils.setField(miniGame, "id", 1L);
        when(miniGameRepository.findById(1L)).thenReturn(Optional.of(miniGame));

        MiniGameScoreUpdateResponse response = gameService.updateMiniGameScore(
                1L,
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

        assertThatThrownBy(() -> gameService.updateMiniGameScore(999L, 1L, new MiniGameScoreUpdateRequest(10, 8)))
                .isInstanceOf(YbcException.class)
                .hasMessage("존재하지 않는 미니 게임입니다.");
    }

    @Test
    @DisplayName("본인이 만든 미니 게임만 수정할 수 있다")
    void updateMiniGameScoreThrowsWhenRequesterIsNotOwner() {
        MiniGame miniGame = MiniGame.create(
                "7월 1주차 미니게임",
                "김민수",
                "이서연",
                "박지훈",
                "최유진",
                21,
                createMember(1L, "3001", "김민수")
        );
        when(miniGameRepository.findById(1L)).thenReturn(Optional.of(miniGame));

        assertThatThrownBy(() -> gameService.updateMiniGameScore(1L, 2L, new MiniGameScoreUpdateRequest(10, 8)))
                .isInstanceOf(YbcException.class)
                .hasMessage("해당 미니 게임을 수정할 권한이 없습니다.");
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
                21,
                createMember(1L, "3001", "김민수")
        );
        when(miniGameRepository.findById(1L)).thenReturn(Optional.of(miniGame));

        assertThatThrownBy(() -> gameService.updateMiniGameScore(1L, 1L, new MiniGameScoreUpdateRequest(22, 8)))
                .isInstanceOf(YbcException.class)
                .hasMessage("팀 점수는 0점 이상 타겟 스코어 이하여야 합니다.");
    }

    @Test
    @DisplayName("미니 게임 종료 시 경기 결과와 종료 상태를 저장한다")
    void endMiniGameStoresResult() {
        MiniGame miniGame = MiniGame.create(
                "7월 1주차 미니게임",
                "김민수",
                "이서연",
                "박지훈",
                "최유진",
                21,
                createMember(1L, "3001", "김민수")
        );
        ReflectionTestUtils.setField(miniGame, "id", 1L);
        when(miniGameRepository.findById(1L)).thenReturn(Optional.of(miniGame));

        MiniGameEndResponse response = gameService.endMiniGame(
                1L,
                1L,
                new MiniGameEndRequest(
                        LocalDate.of(2026, 7, 7),
                        LocalTime.of(19, 0),
                        LocalTime.of(19, 20),
                        21,
                        18,
                        MiniGameWinnerTeam.TEAM1
                )
        );

        assertThat(miniGame.getGameDate()).isEqualTo(LocalDate.of(2026, 7, 7));
        assertThat(miniGame.getStartTime()).isEqualTo(LocalTime.of(19, 0));
        assertThat(miniGame.getEndTime()).isEqualTo(LocalTime.of(19, 20));
        assertThat(miniGame.getWinnerTeam()).isEqualTo(MiniGameWinnerTeam.TEAM1);
        assertThat(miniGame.getStatus()).isEqualTo(MiniGameStatus.FINISHED);
        assertThat(response.gameId()).isEqualTo(1L);
        assertThat(response.winnerTeam()).isEqualTo(MiniGameWinnerTeam.TEAM1);
        assertThat(response.status()).isEqualTo(MiniGameStatus.FINISHED);
    }

    @Test
    @DisplayName("최종 점수와 우승팀이 맞지 않으면 미니 게임 종료에 실패한다")
    void endMiniGameThrowsWhenWinnerTeamIsInvalid() {
        MiniGame miniGame = MiniGame.create(
                "7월 1주차 미니게임",
                "김민수",
                "이서연",
                "박지훈",
                "최유진",
                21,
                createMember(1L, "3001", "김민수")
        );
        when(miniGameRepository.findById(1L)).thenReturn(Optional.of(miniGame));

        assertThatThrownBy(() -> gameService.endMiniGame(
                1L,
                1L,
                new MiniGameEndRequest(
                        LocalDate.of(2026, 7, 7),
                        LocalTime.of(19, 0),
                        LocalTime.of(19, 20),
                        21,
                        18,
                        MiniGameWinnerTeam.TEAM2
                )
        )).isInstanceOf(YbcException.class)
                .hasMessage("우승팀 정보가 최종 점수와 일치하지 않습니다.");
    }

    @Test
    @DisplayName("이미 종료된 미니 게임은 진행 중 점수를 수정할 수 없다")
    void updateMiniGameScoreThrowsWhenMiniGameIsFinished() {
        MiniGame miniGame = MiniGame.create(
                "7월 1주차 미니게임",
                "김민수",
                "이서연",
                "박지훈",
                "최유진",
                21,
                createMember(1L, "3001", "김민수")
        );
        miniGame.finish(
                LocalDate.of(2026, 7, 7),
                LocalTime.of(19, 0),
                LocalTime.of(19, 20),
                21,
                18,
                MiniGameWinnerTeam.TEAM1
        );
        when(miniGameRepository.findById(1L)).thenReturn(Optional.of(miniGame));

        assertThatThrownBy(() -> gameService.updateMiniGameScore(1L, 1L, new MiniGameScoreUpdateRequest(10, 8)))
                .isInstanceOf(YbcException.class)
                .hasMessage("이미 종료된 미니 게임입니다.");
    }

    @Test
    @DisplayName("종료된 미니 게임은 제목, 팀원, 최종 점수를 수정할 수 있다")
    void updateFinishedMiniGameUpdatesFields() {
        MiniGame miniGame = MiniGame.create(
                "7월 1주차 미니게임",
                "김민수",
                "이서연",
                "박지훈",
                "최유진",
                25,
                createMember(1L, "3001", "김민수")
        );
        ReflectionTestUtils.setField(miniGame, "id", 1L);
        miniGame.finish(
                LocalDate.of(2026, 7, 7),
                LocalTime.of(19, 0),
                LocalTime.of(19, 20),
                25,
                20,
                MiniGameWinnerTeam.TEAM1
        );
        when(miniGameRepository.findById(1L)).thenReturn(Optional.of(miniGame));

        MiniGameFinishedUpdateResponse response = gameService.updateFinishedMiniGame(
                1L,
                1L,
                new MiniGameFinishedUpdateRequest(
                        "7월 첫째 주 미니게임",
                        "김민수",
                        "이서연",
                        "박지훈",
                        "최유진",
                        25,
                        23
                )
        );

        assertThat(miniGame.getTitle()).isEqualTo("7월 첫째 주 미니게임");
        assertThat(miniGame.getTeam1Score()).isEqualTo(25);
        assertThat(miniGame.getTeam2Score()).isEqualTo(23);
        assertThat(miniGame.getWinnerTeam()).isEqualTo(MiniGameWinnerTeam.TEAM1);
        assertThat(response.gameId()).isEqualTo(1L);
        assertThat(response.title()).isEqualTo("7월 첫째 주 미니게임");
        assertThat(response.winnerTeam()).isEqualTo(MiniGameWinnerTeam.TEAM1);
    }

    @Test
    @DisplayName("종료되지 않은 미니 게임은 종료 후 수정 API로 수정할 수 없다")
    void updateFinishedMiniGameThrowsWhenMiniGameIsNotFinished() {
        MiniGame miniGame = MiniGame.create(
                "7월 1주차 미니게임",
                "김민수",
                "이서연",
                "박지훈",
                "최유진",
                25,
                createMember(1L, "3001", "김민수")
        );
        when(miniGameRepository.findById(1L)).thenReturn(Optional.of(miniGame));

        assertThatThrownBy(() -> gameService.updateFinishedMiniGame(
                1L,
                1L,
                new MiniGameFinishedUpdateRequest(
                        "7월 첫째 주 미니게임",
                        "김민수",
                        "이서연",
                        "박지훈",
                        "최유진",
                        25,
                        23
                )
        )).isInstanceOf(YbcException.class)
                .hasMessage("종료된 미니 게임만 수정할 수 있습니다.");
    }

    @Test
    @DisplayName("작성자는 본인이 만든 미니 게임을 삭제할 수 있다")
    void deleteMiniGameDeletesOwnedGame() {
        MiniGame miniGame = MiniGame.create(
                "7월 1주차 미니게임",
                "김민수",
                "이서연",
                "박지훈",
                "최유진",
                21,
                createMember(1L, "3001", "김민수")
        );
        when(miniGameRepository.findById(1L)).thenReturn(Optional.of(miniGame));

        gameService.deleteMiniGame(1L, 1L);

        verify(miniGameRepository).delete(miniGame);
    }

    @Test
    @DisplayName("작성자가 아니면 미니 게임을 삭제할 수 없다")
    void deleteMiniGameThrowsWhenRequesterIsNotOwner() {
        MiniGame miniGame = MiniGame.create(
                "7월 1주차 미니게임",
                "김민수",
                "이서연",
                "박지훈",
                "최유진",
                21,
                createMember(1L, "3001", "김민수")
        );
        when(miniGameRepository.findById(1L)).thenReturn(Optional.of(miniGame));

        assertThatThrownBy(() -> gameService.deleteMiniGame(1L, 2L))
                .isInstanceOf(YbcException.class)
                .hasMessage("해당 미니 게임을 삭제할 권한이 없습니다.");
    }

    @Test
    @DisplayName("존재하지 않는 미니 게임은 삭제할 수 없다")
    void deleteMiniGameThrowsWhenMiniGameIsMissing() {
        when(miniGameRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gameService.deleteMiniGame(999L, 1L))
                .isInstanceOf(YbcException.class)
                .hasMessage("존재하지 않는 미니 게임입니다.");
    }

    @Test
    @DisplayName("7일이 지난 미니 게임 정리 시 게임 날짜와 생성일 기준 삭제 조건을 사용한다")
    void deleteExpiredMiniGamesUsesSevenDayCutoff() {
        when(miniGameRepository.deleteExpiredMiniGames(any(LocalDate.class), any(LocalDateTime.class))).thenReturn(2);

        int deletedCount = gameService.deleteExpiredMiniGames();

        assertThat(deletedCount).isEqualTo(2);
        verify(miniGameRepository).deleteExpiredMiniGames(
                LocalDate.of(2026, 7, 7),
                LocalDateTime.of(2026, 7, 7, 12, 0)
        );
    }

    private Member createMember(Long id, String socialId, String nickname) {
        Member member = Member.createSocialMember(SocialProvider.KAKAO, socialId, nickname, socialId + "@example.com");
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}
