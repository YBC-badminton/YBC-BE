package com.ybc.ybc.minigames.controller;

import com.ybc.ybc.minigames.dto.request.MiniGameCreateRequest;
import com.ybc.ybc.minigames.dto.request.MiniGameScoreUpdateRequest;
import com.ybc.ybc.minigames.dto.response.MiniGameCreateResponse;
import com.ybc.ybc.minigames.dto.response.MiniGameScoreUpdateResponse;
import com.ybc.ybc.minigames.service.GameService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/games")
public class GameController {
    private final GameService gameService;

    @PostMapping
    public ResponseEntity<MiniGameCreateResponse> createMiniGame(@Valid @RequestBody MiniGameCreateRequest request) {
        return ResponseEntity.ok(gameService.createMiniGame(request));
    }

    @PatchMapping("/{gameId}/score")
    public ResponseEntity<MiniGameScoreUpdateResponse> updateMiniGameScore(
            @PathVariable Long gameId,
            @Valid @RequestBody MiniGameScoreUpdateRequest request
    ) {
        return ResponseEntity.ok(gameService.updateMiniGameScore(gameId, request));
    }
}
