package com.ybc.ybc.minigames.controller;

import com.ybc.ybc.auth.domain.MemberPrincipal;
import com.ybc.ybc.minigames.dto.request.MiniGameCreateRequest;
import com.ybc.ybc.minigames.dto.request.MiniGameEndRequest;
import com.ybc.ybc.minigames.dto.request.MiniGameFinishedUpdateRequest;
import com.ybc.ybc.minigames.dto.request.MiniGameScoreUpdateRequest;
import com.ybc.ybc.minigames.dto.response.MiniGameCreateResponse;
import com.ybc.ybc.minigames.dto.response.MiniGameEndResponse;
import com.ybc.ybc.minigames.dto.response.MiniGameFinishedUpdateResponse;
import com.ybc.ybc.minigames.dto.response.MiniGameScoreUpdateResponse;
import com.ybc.ybc.minigames.service.GameService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    // 미니 게임 생성
    @PostMapping
    public ResponseEntity<MiniGameCreateResponse> createMiniGame(
            @AuthenticationPrincipal MemberPrincipal memberPrincipal,
            @Valid @RequestBody MiniGameCreateRequest request
    ) {
        return ResponseEntity.ok(gameService.createMiniGame(memberPrincipal.getId(), request));
    }

    // 진행 중인 미니 게임 스코어 수정
    @PatchMapping("/{gameId}/score")
    public ResponseEntity<MiniGameScoreUpdateResponse> updateMiniGameScore(
            @PathVariable Long gameId,
            @AuthenticationPrincipal MemberPrincipal memberPrincipal,
            @Valid @RequestBody MiniGameScoreUpdateRequest request
    ) {
        return ResponseEntity.ok(gameService.updateMiniGameScore(gameId, memberPrincipal.getId(), request));
    }

    // 미니 게임 종료 후 저장
    @PatchMapping("/{gameId}/end")
    public ResponseEntity<MiniGameEndResponse> endMiniGame(
            @PathVariable Long gameId,
            @AuthenticationPrincipal MemberPrincipal memberPrincipal,
            @Valid @RequestBody MiniGameEndRequest request
    ) {
        return ResponseEntity.ok(gameService.endMiniGame(gameId, memberPrincipal.getId(), request));
    }

    // 종료된 미니 게임 수정
    @PatchMapping("/{gameId}")
    public ResponseEntity<MiniGameFinishedUpdateResponse> updateFinishedMiniGame(
            @PathVariable Long gameId,
            @AuthenticationPrincipal MemberPrincipal memberPrincipal,
            @Valid @RequestBody MiniGameFinishedUpdateRequest request
    ) {
        return ResponseEntity.ok(gameService.updateFinishedMiniGame(gameId, memberPrincipal.getId(), request));
    }
}
