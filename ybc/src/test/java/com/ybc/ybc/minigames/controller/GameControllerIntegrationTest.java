package com.ybc.ybc.minigames.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.domain.MemberPrincipal;
import com.ybc.ybc.auth.domain.SocialProvider;
import com.ybc.ybc.auth.dto.MemberSummary;
import com.ybc.ybc.auth.repository.MemberRepository;
import com.ybc.ybc.minigames.domain.MiniGame;
import com.ybc.ybc.minigames.domain.MiniGameWinnerTeam;
import com.ybc.ybc.minigames.repository.MiniGameRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
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

    @Autowired
    private MemberRepository memberRepository;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    @Test
    @DisplayName("인증된 사용자는 종료된 미니 게임 기록을 최신 순으로 조회할 수 있다")
    void getGames() throws Exception {
        Member member = saveMember("3001", "김민수");

        MiniGame latestGame = miniGameRepository.save(
                MiniGame.create(
                        "7월 첫째 주 미니게임",
                        "김민수",
                        "이서연",
                        "박지훈",
                        "최유진",
                        25,
                        member
                )
        );
        latestGame.finish(
                java.time.LocalDate.of(2026, 7, 19),
                java.time.LocalTime.of(14, 0),
                java.time.LocalTime.of(14, 25),
                25,
                23,
                MiniGameWinnerTeam.TEAM1
        );

        MiniGame previousGame = miniGameRepository.save(
                MiniGame.create(
                        "초급 복식 경기",
                        "정우성",
                        "한지민",
                        "김도현",
                        "이수빈",
                        21,
                        member
                )
        );
        previousGame.finish(
                java.time.LocalDate.of(2026, 7, 18),
                java.time.LocalTime.of(15, 10),
                java.time.LocalTime.of(15, 35),
                18,
                21,
                MiniGameWinnerTeam.TEAM2
        );

        miniGameRepository.save(
                MiniGame.create(
                        "진행 중 미니게임",
                        "A",
                        "B",
                        "C",
                        "D",
                        21,
                        member
                )
        );

        mockMvc.perform(get("/games")
                        .with(authorizedUser(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.games.length()").value(2))
                .andExpect(jsonPath("$.games[0].gameId").value(latestGame.getId()))
                .andExpect(jsonPath("$.games[0].memberId").value(member.getId()))
                .andExpect(jsonPath("$.games[0].title").value("7월 첫째 주 미니게임"))
                .andExpect(jsonPath("$.games[0].writer").value("김민수"))
                .andExpect(jsonPath("$.games[0].team1Member1").value("김민수"))
                .andExpect(jsonPath("$.games[0].team1Member2").value("이서연"))
                .andExpect(jsonPath("$.games[0].team2Member1").value("박지훈"))
                .andExpect(jsonPath("$.games[0].team2Member2").value("최유진"))
                .andExpect(jsonPath("$.games[0].team1Score").value(25))
                .andExpect(jsonPath("$.games[0].team2Score").value(23))
                .andExpect(jsonPath("$.games[0].winnerTeam").value("TEAM1"))
                .andExpect(jsonPath("$.games[0].startTime").value("2026-07-19T14:00:00"))
                .andExpect(jsonPath("$.games[0].endTime").value("2026-07-19T14:25:00"))
                .andExpect(jsonPath("$.games[1].gameId").value(previousGame.getId()))
                .andExpect(jsonPath("$.games[1].memberId").value(member.getId()))
                .andExpect(jsonPath("$.games[1].writer").value("김민수"))
                .andExpect(jsonPath("$.games[1].winnerTeam").value("TEAM2"))
                .andExpect(jsonPath("$.games[1].startTime").value("2026-07-18T15:10:00"))
                .andExpect(jsonPath("$.games[1].endTime").value("2026-07-18T15:35:00"));
    }

    @Test
    @DisplayName("인증되지 않은 사용자는 미니 게임 기록을 조회할 수 없다")
    void getGamesRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/games"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_USER"));
    }

    @Test
    @DisplayName("카카오 로그인 회원은 미니 게임을 생성할 수 있다")
    void createMiniGame() throws Exception {
        Member member = saveMember("3001", "김민수");

        mockMvc.perform(post("/games")
                        .with(authorizedUser(member))
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
                .andExpect(jsonPath("$.memberId").value(member.getId()))
                .andExpect(jsonPath("$.title").value("7월 1주차 미니게임"))
                .andExpect(jsonPath("$.writer").value("김민수"))
                .andExpect(jsonPath("$.team1Member1").value("김민수"))
                .andExpect(jsonPath("$.team1Member2").value("이서연"))
                .andExpect(jsonPath("$.team2Member1").value("박지훈"))
                .andExpect(jsonPath("$.team2Member2").value("최유진"))
                .andExpect(jsonPath("$.targetScore").value(21))
                .andExpect(jsonPath("$.team1Score").value(0))
                .andExpect(jsonPath("$.team2Score").value(0))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        MiniGame savedMiniGame = miniGameRepository.findAll().get(0);
        assertThat(savedMiniGame.getMember().getId()).isEqualTo(member.getId());
        assertThat(savedMiniGame.getTargetScore()).isEqualTo(21);
    }

    @Test
    @DisplayName("타겟 스코어가 누락되면 미니 게임 생성에 실패한다")
    void createMiniGameThrowsWhenTargetScoreIsMissing() throws Exception {
        Member member = saveMember("3001", "김민수");

        mockMvc.perform(post("/games")
                        .with(authorizedUser(member))
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
    @DisplayName("작성자는 진행 중인 미니 게임 스코어를 수정할 수 있다")
    void updateMiniGameScore() throws Exception {
        Member owner = saveMember("3001", "김민수");
        MiniGame miniGame = miniGameRepository.save(
                MiniGame.create(
                        "7월 1주차 미니게임",
                        "김민수",
                        "이서연",
                        "박지훈",
                        "최유진",
                        21,
                        owner
                )
        );

        mockMvc.perform(patch("/games/{gameId}/score", miniGame.getId())
                        .with(authorizedUser(owner))
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
    @DisplayName("본인이 만든 미니 게임이 아니면 점수를 수정할 수 없다")
    void updateMiniGameScoreThrowsWhenRequesterIsNotOwner() throws Exception {
        Member owner = saveMember("3001", "김민수");
        Member anotherMember = saveMember("3002", "이서연");
        MiniGame miniGame = miniGameRepository.save(
                MiniGame.create(
                        "7월 1주차 미니게임",
                        "김민수",
                        "이서연",
                        "박지훈",
                        "최유진",
                        21,
                        owner
                )
        );

        mockMvc.perform(patch("/games/{gameId}/score", miniGame.getId())
                        .with(authorizedUser(anotherMember))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "team1Score", 10,
                                "team2Score", 8
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("MINI_GAME_UPDATE_FORBIDDEN"));
    }

    @Test
    @DisplayName("존재하지 않는 미니 게임은 스코어를 수정할 수 없다")
    void updateMiniGameScoreThrowsWhenMiniGameIsMissing() throws Exception {
        Member member = saveMember("3001", "김민수");

        mockMvc.perform(patch("/games/{gameId}/score", 999L)
                        .with(authorizedUser(member))
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
        Member owner = saveMember("3001", "김민수");
        MiniGame miniGame = miniGameRepository.save(
                MiniGame.create(
                        "7월 1주차 미니게임",
                        "김민수",
                        "이서연",
                        "박지훈",
                        "최유진",
                        21,
                        owner
                )
        );

        mockMvc.perform(patch("/games/{gameId}/score", miniGame.getId())
                        .with(authorizedUser(owner))
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
    @DisplayName("작성자는 미니 게임을 종료할 수 있다")
    void endMiniGame() throws Exception {
        Member owner = saveMember("3001", "김민수");
        MiniGame miniGame = miniGameRepository.save(
                MiniGame.create(
                        "7월 1주차 미니게임",
                        "김민수",
                        "이서연",
                        "박지훈",
                        "최유진",
                        21,
                        owner
                )
        );

        mockMvc.perform(patch("/games/{gameId}/end", miniGame.getId())
                        .with(authorizedUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "gameDate", "2026-07-07",
                                "startTime", "19:00",
                                "endTime", "19:20",
                                "team1Score", 21,
                                "team2Score", 18,
                                "winnerTeam", "TEAM1"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").value(miniGame.getId()))
                .andExpect(jsonPath("$.title").value("7월 1주차 미니게임"))
                .andExpect(jsonPath("$.team1Score").value(21))
                .andExpect(jsonPath("$.team2Score").value(18))
                .andExpect(jsonPath("$.winnerTeam").value("TEAM1"))
                .andExpect(jsonPath("$.status").value("FINISHED"));

        MiniGame finishedMiniGame = miniGameRepository.findById(miniGame.getId()).orElseThrow();
        assertThat(finishedMiniGame.getWinnerTeam()).isEqualTo(MiniGameWinnerTeam.TEAM1);
        assertThat(finishedMiniGame.getStatus().name()).isEqualTo("FINISHED");
    }

    @Test
    @DisplayName("본인이 만든 미니 게임이 아니면 종료할 수 없다")
    void endMiniGameThrowsWhenRequesterIsNotOwner() throws Exception {
        Member owner = saveMember("3001", "김민수");
        Member anotherMember = saveMember("3002", "이서연");
        MiniGame miniGame = miniGameRepository.save(
                MiniGame.create(
                        "7월 1주차 미니게임",
                        "김민수",
                        "이서연",
                        "박지훈",
                        "최유진",
                        21,
                        owner
                )
        );

        mockMvc.perform(patch("/games/{gameId}/end", miniGame.getId())
                        .with(authorizedUser(anotherMember))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "gameDate", "2026-07-07",
                                "startTime", "19:00",
                                "endTime", "19:20",
                                "team1Score", 21,
                                "team2Score", 18,
                                "winnerTeam", "TEAM1"
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("MINI_GAME_UPDATE_FORBIDDEN"));
    }

    @Test
    @DisplayName("우승팀 정보가 점수와 다르면 미니 게임 종료에 실패한다")
    void endMiniGameThrowsWhenWinnerTeamIsInvalid() throws Exception {
        Member owner = saveMember("3001", "김민수");
        MiniGame miniGame = miniGameRepository.save(
                MiniGame.create(
                        "7월 1주차 미니게임",
                        "김민수",
                        "이서연",
                        "박지훈",
                        "최유진",
                        21,
                        owner
                )
        );

        mockMvc.perform(patch("/games/{gameId}/end", miniGame.getId())
                        .with(authorizedUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "gameDate", "2026-07-07",
                                "startTime", "19:00",
                                "endTime", "19:20",
                                "team1Score", 21,
                                "team2Score", 18,
                                "winnerTeam", "TEAM2"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_MINI_GAME_WINNER_TEAM"));
    }

    @Test
    @DisplayName("작성자는 종료된 미니 게임을 수정할 수 있다")
    void updateFinishedMiniGame() throws Exception {
        Member owner = saveMember("3001", "김민수");
        MiniGame miniGame = miniGameRepository.save(
                MiniGame.create(
                        "7월 1주차 미니게임",
                        "김민수",
                        "이서연",
                        "박지훈",
                        "최유진",
                        25,
                        owner
                )
        );
        miniGame.finish(
                java.time.LocalDate.of(2026, 7, 7),
                java.time.LocalTime.of(19, 0),
                java.time.LocalTime.of(19, 20),
                25,
                20,
                MiniGameWinnerTeam.TEAM1
        );

        mockMvc.perform(patch("/games/{gameId}", miniGame.getId())
                        .with(authorizedUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "7월 첫째 주 미니게임",
                                "team1Member1", "김민수",
                                "team1Member2", "이서연",
                                "team2Member1", "박지훈",
                                "team2Member2", "최유진",
                                "team1Score", 25,
                                "team2Score", 23
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").value(miniGame.getId()))
                .andExpect(jsonPath("$.title").value("7월 첫째 주 미니게임"))
                .andExpect(jsonPath("$.team1Member1").value("김민수"))
                .andExpect(jsonPath("$.team1Member2").value("이서연"))
                .andExpect(jsonPath("$.team2Member1").value("박지훈"))
                .andExpect(jsonPath("$.team2Member2").value("최유진"))
                .andExpect(jsonPath("$.team1Score").value(25))
                .andExpect(jsonPath("$.team2Score").value(23))
                .andExpect(jsonPath("$.winnerTeam").value("TEAM1"));
    }

    @Test
    @DisplayName("본인이 만든 미니 게임이 아니면 종료 후 수정할 수 없다")
    void updateFinishedMiniGameThrowsWhenRequesterIsNotOwner() throws Exception {
        Member owner = saveMember("3001", "김민수");
        Member anotherMember = saveMember("3002", "이서연");
        MiniGame miniGame = miniGameRepository.save(
                MiniGame.create(
                        "7월 1주차 미니게임",
                        "김민수",
                        "이서연",
                        "박지훈",
                        "최유진",
                        25,
                        owner
                )
        );
        miniGame.finish(
                java.time.LocalDate.of(2026, 7, 7),
                java.time.LocalTime.of(19, 0),
                java.time.LocalTime.of(19, 20),
                25,
                20,
                MiniGameWinnerTeam.TEAM1
        );

        mockMvc.perform(patch("/games/{gameId}", miniGame.getId())
                        .with(authorizedUser(anotherMember))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "7월 첫째 주 미니게임",
                                "team1Member1", "김민수",
                                "team1Member2", "이서연",
                                "team2Member1", "박지훈",
                                "team2Member2", "최유진",
                                "team1Score", 25,
                                "team2Score", 23
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("MINI_GAME_UPDATE_FORBIDDEN"));
    }

    @Test
    @DisplayName("종료되지 않은 미니 게임은 종료 후 수정 API로 수정할 수 없다")
    void updateFinishedMiniGameThrowsWhenMiniGameIsNotFinished() throws Exception {
        Member owner = saveMember("3001", "김민수");
        MiniGame miniGame = miniGameRepository.save(
                MiniGame.create(
                        "7월 1주차 미니게임",
                        "김민수",
                        "이서연",
                        "박지훈",
                        "최유진",
                        25,
                        owner
                )
        );

        mockMvc.perform(patch("/games/{gameId}", miniGame.getId())
                        .with(authorizedUser(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "7월 첫째 주 미니게임",
                                "team1Member1", "김민수",
                                "team1Member2", "이서연",
                                "team2Member1", "박지훈",
                                "team2Member2", "최유진",
                                "team1Score", 25,
                                "team2Score", 23
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MINI_GAME_NOT_FINISHED"));
    }

    @Test
    @DisplayName("인증되지 않은 사용자는 미니 게임 스코어를 수정할 수 없다")
    void updateMiniGameScoreRequiresAuthentication() throws Exception {
        Member owner = saveMember("3001", "김민수");
        MiniGame miniGame = miniGameRepository.save(
                MiniGame.create(
                        "7월 1주차 미니게임",
                        "김민수",
                        "이서연",
                        "박지훈",
                        "최유진",
                        21,
                        owner
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

    private Member saveMember(String socialId, String nickname) {
        return memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, socialId, nickname, socialId + "@example.com")
        );
    }

    private RequestPostProcessor authorizedUser(Member member) {
        MemberPrincipal principal = MemberPrincipal.from(MemberSummary.from(member));
        return authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }
}
