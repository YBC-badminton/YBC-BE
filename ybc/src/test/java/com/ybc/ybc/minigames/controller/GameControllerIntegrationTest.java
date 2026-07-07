package com.ybc.ybc.minigames.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ybc.ybc.minigames.domain.MiniGame;
import com.ybc.ybc.minigames.repository.MiniGameRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GameControllerIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MiniGameRepository miniGameRepository;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    @Test
    @DisplayName("인증된 사용자는 미니 게임을 생성할 수 있다")
    void createMiniGame() throws Exception {
        mockMvc.perform(post("/games")
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "7월 1주차 미니게임",
                                "team1Member1", "김민수",
                                "team1Member2", "이서연",
                                "team2Member1", "박지훈",
                                "team2Member2", "최유진",
                                "targetScore", 21
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").isNumber())
                .andExpect(jsonPath("$.title").value("7월 1주차 미니게임"))
                .andExpect(jsonPath("$.team1Member1").value("김민수"))
                .andExpect(jsonPath("$.team1Member2").value("이서연"))
                .andExpect(jsonPath("$.team2Member1").value("박지훈"))
                .andExpect(jsonPath("$.team2Member2").value("최유진"))
                .andExpect(jsonPath("$.targetScore").value(21))
                .andExpect(jsonPath("$.team1Score").value(0))
                .andExpect(jsonPath("$.team2Score").value(0))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        MiniGame savedMiniGame = miniGameRepository.findAll().get(0);
        assertThat(savedMiniGame.getTitle()).isEqualTo("7월 1주차 미니게임");
        assertThat(savedMiniGame.getTargetScore()).isEqualTo(21);
        assertThat(savedMiniGame.getTeam1Score()).isEqualTo(0);
        assertThat(savedMiniGame.getTeam2Score()).isEqualTo(0);
    }

    @Test
    @DisplayName("타겟 스코어가 누락되면 미니 게임 생성에 실패한다")
    void createMiniGameThrowsWhenTargetScoreIsMissing() throws Exception {
        mockMvc.perform(post("/games")
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "7월 1주차 미니게임",
                                  "team1Member1": "김민수",
                                  "team1Member2": "이서연",
                                  "team2Member1": "박지훈",
                                  "team2Member2": "최유진"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("타겟 스코어는 필수입니다."));
    }

    @Test
    @DisplayName("인증되지 않은 사용자는 미니 게임을 생성할 수 없다")
    void createMiniGameRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "7월 1주차 미니게임",
                                "team1Member1", "김민수",
                                "team1Member2", "이서연",
                                "team2Member1", "박지훈",
                                "team2Member2", "최유진",
                                "targetScore", 21
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_USER"));
    }

    @Test
    @DisplayName("인증된 사용자는 진행 중인 미니 게임 스코어를 수정할 수 있다")
    void updateMiniGameScore() throws Exception {
        MiniGame miniGame = miniGameRepository.save(
                MiniGame.create(
                        "7월 1주차 미니게임",
                        "김민수",
                        "이서연",
                        "박지훈",
                        "최유진",
                        21
                )
        );

        mockMvc.perform(patch("/games/{gameId}/score", miniGame.getId())
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "team1Score", 10,
                                "team2Score", 8
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").value(miniGame.getId()))
                .andExpect(jsonPath("$.team1Score").value(10))
                .andExpect(jsonPath("$.team2Score").value(8));

        MiniGame updatedMiniGame = miniGameRepository.findById(miniGame.getId()).orElseThrow();
        assertThat(updatedMiniGame.getTeam1Score()).isEqualTo(10);
        assertThat(updatedMiniGame.getTeam2Score()).isEqualTo(8);
    }

    @Test
    @DisplayName("존재하지 않는 미니 게임은 스코어를 수정할 수 없다")
    void updateMiniGameScoreThrowsWhenMiniGameIsMissing() throws Exception {
        mockMvc.perform(patch("/games/{gameId}/score", 999L)
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "team1Score", 10,
                                "team2Score", 8
                        ))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("MINI_GAME_NOT_FOUND"));
    }

    @Test
    @DisplayName("타겟 스코어를 넘는 점수는 수정할 수 없다")
    void updateMiniGameScoreThrowsWhenScoreExceedsTargetScore() throws Exception {
        MiniGame miniGame = miniGameRepository.save(
                MiniGame.create(
                        "7월 1주차 미니게임",
                        "김민수",
                        "이서연",
                        "박지훈",
                        "최유진",
                        21
                )
        );

        mockMvc.perform(patch("/games/{gameId}/score", miniGame.getId())
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "team1Score", 22,
                                "team2Score", 8
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_MINI_GAME_SCORE"))
                .andExpect(jsonPath("$.message").value("팀 점수는 0점 이상 타겟 스코어 이하여야 합니다."));
    }

    @Test
    @DisplayName("인증되지 않은 사용자는 미니 게임 스코어를 수정할 수 없다")
    void updateMiniGameScoreRequiresAuthentication() throws Exception {
        MiniGame miniGame = miniGameRepository.save(
                MiniGame.create(
                        "7월 1주차 미니게임",
                        "김민수",
                        "이서연",
                        "박지훈",
                        "최유진",
                        21
                )
        );

        mockMvc.perform(patch("/games/{gameId}/score", miniGame.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "team1Score", 10,
                                "team2Score", 8
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_USER"));
    }

    private JwtRequestPostProcessor authorizedUser() {
        return jwt().jwt(jwt -> jwt
                .subject("1")
                .claim("tokenType", "access")
                .claim("provider", "KAKAO")
                .claim("socialId", "123456789")
                .claim("email", "admin@example.com")
                .claim("nickname", "admin"));
    }
}
