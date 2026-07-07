package com.ybc.ybc.minigames.repository;

import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.domain.SocialProvider;
import com.ybc.ybc.auth.repository.MemberRepository;
import com.ybc.ybc.minigames.domain.MiniGame;
import com.ybc.ybc.minigames.domain.MiniGameWinnerTeam;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class MiniGameRepositoryTest {
    @Autowired
    private MiniGameRepository miniGameRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("게임 날짜가 7일 지난 종료된 미니 게임은 삭제한다")
    void deleteExpiredMiniGamesDeletesFinishedGamesByGameDate() {
        Member member = saveMember("2001", "김민수");
        MiniGame expiredMiniGame = saveFinishedMiniGame(member, LocalDate.of(2026, 7, 7));
        MiniGame recentMiniGame = saveFinishedMiniGame(member, LocalDate.of(2026, 7, 8));

        int deletedCount = miniGameRepository.deleteExpiredMiniGames(
                LocalDate.of(2026, 7, 7),
                LocalDateTime.of(2026, 7, 7, 0, 0)
        );

        assertThat(deletedCount).isEqualTo(1);
        assertThat(miniGameRepository.findById(expiredMiniGame.getId())).isEmpty();
        assertThat(miniGameRepository.findById(recentMiniGame.getId())).isPresent();
    }

    @Test
    @DisplayName("게임 날짜가 없는 진행 중 미니 게임은 생성일 기준 7일 후 삭제한다")
    void deleteExpiredMiniGamesDeletesInProgressGamesByCreatedAt() {
        Member member = saveMember("2002", "이서연");
        MiniGame expiredMiniGame = MiniGame.create(
                "오래된 진행 중 게임",
                "김민수",
                "이서연",
                "박지훈",
                "최유진",
                21,
                member
        );
        MiniGame recentMiniGame = MiniGame.create(
                "최근 진행 중 게임",
                "김민수",
                "이서연",
                "박지훈",
                "최유진",
                21,
                member
        );
        expiredMiniGame = miniGameRepository.saveAndFlush(expiredMiniGame);
        recentMiniGame = miniGameRepository.saveAndFlush(recentMiniGame);
        updateCreatedAt(expiredMiniGame.getId(), LocalDateTime.of(2026, 7, 7, 12, 0));
        updateCreatedAt(recentMiniGame.getId(), LocalDateTime.of(2026, 7, 8, 12, 0));

        int deletedCount = miniGameRepository.deleteExpiredMiniGames(
                LocalDate.of(2026, 7, 7),
                LocalDateTime.of(2026, 7, 7, 12, 0)
        );

        assertThat(deletedCount).isEqualTo(1);
        assertThat(miniGameRepository.findById(expiredMiniGame.getId())).isEmpty();
        assertThat(miniGameRepository.findById(recentMiniGame.getId())).isPresent();
    }

    private Member saveMember(String socialId, String nickname) {
        Member member = Member.createSocialMember(
                SocialProvider.KAKAO,
                socialId,
                nickname,
                socialId + "@example.com"
        );
        member.updateProfile(nickname, socialId + "@example.com");
        return memberRepository.saveAndFlush(member);
    }

    private MiniGame saveFinishedMiniGame(Member member, LocalDate gameDate) {
        MiniGame miniGame = MiniGame.create(
                gameDate + " 미니게임",
                "김민수",
                "이서연",
                "박지훈",
                "최유진",
                21,
                member
        );
        miniGame.finish(
                gameDate,
                LocalTime.of(19, 0),
                LocalTime.of(19, 20),
                21,
                18,
                MiniGameWinnerTeam.TEAM1
        );
        return miniGameRepository.saveAndFlush(miniGame);
    }

    private void updateCreatedAt(Long gameId, LocalDateTime createdAt) {
        jdbcTemplate.update(
                "update mini_game set created_at = ? where mini_game_id = ?",
                Timestamp.valueOf(createdAt),
                gameId
        );
    }
}
