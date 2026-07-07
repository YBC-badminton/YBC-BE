package com.ybc.ybc.minigames.service;

import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.service.MemberService;
import com.ybc.ybc.minigames.domain.MiniGame;
import com.ybc.ybc.minigames.dto.request.MiniGameCreateRequest;
import com.ybc.ybc.minigames.dto.request.MiniGameEndRequest;
import com.ybc.ybc.minigames.dto.request.MiniGameFinishedUpdateRequest;
import com.ybc.ybc.minigames.dto.request.MiniGameScoreUpdateRequest;
import com.ybc.ybc.minigames.dto.response.MiniGameCreateResponse;
import com.ybc.ybc.minigames.dto.response.MiniGameEndResponse;
import com.ybc.ybc.minigames.dto.response.MiniGameFinishedUpdateResponse;
import com.ybc.ybc.minigames.dto.response.MiniGameScoreUpdateResponse;
import com.ybc.ybc.minigames.repository.MiniGameRepository;
import com.ybc.ybc.global.exception.ExceptionCode;
import com.ybc.ybc.global.exception.YbcException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameService {
    private final MiniGameRepository miniGameRepository;
    private final MemberService memberService;
    private final Clock clock;

    @Transactional
    public MiniGameCreateResponse createMiniGame(Long memberId, MiniGameCreateRequest request) {
        Member member = memberService.getMemberById(memberId);
        MiniGame miniGame = miniGameRepository.save(
                MiniGame.create(
                        request.title(),
                        request.team1Member1(),
                        request.team1Member2(),
                        request.team2Member1(),
                        request.team2Member2(),
                        request.targetScore(),
                        member
                )
        );

        return MiniGameCreateResponse.from(miniGame);
    }

    @Transactional
    public MiniGameScoreUpdateResponse updateMiniGameScore(Long gameId, Long memberId, MiniGameScoreUpdateRequest request) {
        MiniGame miniGame = getMiniGameById(gameId);
        validateOwner(miniGame, memberId);
        miniGame.updateScore(request.team1Score(), request.team2Score());
        return MiniGameScoreUpdateResponse.from(miniGame);
    }

    @Transactional
    public MiniGameEndResponse endMiniGame(Long gameId, Long memberId, MiniGameEndRequest request) {
        MiniGame miniGame = getMiniGameById(gameId);
        validateOwner(miniGame, memberId);
        miniGame.finish(
                request.gameDate(),
                request.startTime(),
                request.endTime(),
                request.team1Score(),
                request.team2Score(),
                request.winnerTeam()
        );
        return MiniGameEndResponse.from(miniGame);
    }

    @Transactional
    public MiniGameFinishedUpdateResponse updateFinishedMiniGame(Long gameId, Long memberId, MiniGameFinishedUpdateRequest request) {
        MiniGame miniGame = getMiniGameById(gameId);
        validateOwner(miniGame, memberId);
        miniGame.updateFinishedGame(
                request.title(),
                request.team1Member1(),
                request.team1Member2(),
                request.team2Member1(),
                request.team2Member2(),
                request.team1Score(),
                request.team2Score()
        );
        return MiniGameFinishedUpdateResponse.from(miniGame);
    }

    @Transactional
    public int deleteExpiredMiniGames() {
        LocalDate gameDateCutoff = LocalDate.now(clock).minusDays(7);
        LocalDateTime createdAtCutoff = LocalDateTime.now(clock).minusDays(7);
        return miniGameRepository.deleteExpiredMiniGames(gameDateCutoff, createdAtCutoff);
    }

    private MiniGame getMiniGameById(Long gameId) {
        return miniGameRepository.findById(gameId)
                .orElseThrow(() -> new YbcException(ExceptionCode.MINI_GAME_NOT_FOUND));
    }

    private void validateOwner(MiniGame miniGame, Long memberId) {
        if (!miniGame.getMember().getId().equals(memberId)) {
            throw new YbcException(ExceptionCode.MINI_GAME_UPDATE_FORBIDDEN);
        }
    }
}
