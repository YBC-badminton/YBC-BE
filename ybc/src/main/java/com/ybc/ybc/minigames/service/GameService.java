package com.ybc.ybc.minigames.service;

import com.ybc.ybc.minigames.domain.MiniGame;
import com.ybc.ybc.minigames.dto.request.MiniGameCreateRequest;
import com.ybc.ybc.minigames.dto.request.MiniGameScoreUpdateRequest;
import com.ybc.ybc.minigames.dto.response.MiniGameCreateResponse;
import com.ybc.ybc.minigames.dto.response.MiniGameScoreUpdateResponse;
import com.ybc.ybc.minigames.repository.MiniGameRepository;
import com.ybc.ybc.global.exception.ExceptionCode;
import com.ybc.ybc.global.exception.YbcException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameService {
    private final MiniGameRepository miniGameRepository;

    @Transactional
    public MiniGameCreateResponse createMiniGame(MiniGameCreateRequest request) {
        MiniGame miniGame = miniGameRepository.save(
                MiniGame.create(
                        request.title(),
                        request.team1Member1(),
                        request.team1Member2(),
                        request.team2Member1(),
                        request.team2Member2(),
                        request.targetScore()
                )
        );

        return MiniGameCreateResponse.from(miniGame);
    }

    @Transactional
    public MiniGameScoreUpdateResponse updateMiniGameScore(Long gameId, MiniGameScoreUpdateRequest request) {
        MiniGame miniGame = getMiniGameById(gameId);
        miniGame.updateScore(request.team1Score(), request.team2Score());
        return MiniGameScoreUpdateResponse.from(miniGame);
    }

    private MiniGame getMiniGameById(Long gameId) {
        return miniGameRepository.findById(gameId)
                .orElseThrow(() -> new YbcException(ExceptionCode.MINI_GAME_NOT_FOUND));
    }
}
