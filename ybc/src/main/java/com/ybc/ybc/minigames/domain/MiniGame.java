package com.ybc.ybc.minigames.domain;

import com.ybc.ybc.global.domain.BaseEntity;
import com.ybc.ybc.global.exception.ExceptionCode;
import com.ybc.ybc.global.exception.YbcException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Set;

@Getter
@Entity
@Table(name = "mini_game")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MiniGame extends BaseEntity {
    private static final Set<Integer> ALLOWED_TARGET_SCORES = Set.of(11, 21, 25);
    private static final int INITIAL_SCORE = 0;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mini_game_id")
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(name = "team1_member1", nullable = false, length = 50)
    private String team1Member1;

    @Column(name = "team1_member2", nullable = false, length = 50)
    private String team1Member2;

    @Column(name = "team2_member1", nullable = false, length = 50)
    private String team2Member1;

    @Column(name = "team2_member2", nullable = false, length = 50)
    private String team2Member2;

    @Column(name = "target_score", nullable = false)
    private int targetScore;

    @Column(name = "team1_score", nullable = false)
    private int team1Score;

    @Column(name = "team2_score", nullable = false)
    private int team2Score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MiniGameStatus status;

    @Builder
    private MiniGame(String title,
                     String team1Member1,
                     String team1Member2,
                     String team2Member1,
                     String team2Member2,
                     Integer targetScore,
                     int team1Score,
                     int team2Score,
                     MiniGameStatus status) {
        validateTargetScore(targetScore);
        validateScore(team1Score, targetScore);
        validateScore(team2Score, targetScore);
        this.title = title;
        this.team1Member1 = team1Member1;
        this.team1Member2 = team1Member2;
        this.team2Member1 = team2Member1;
        this.team2Member2 = team2Member2;
        this.targetScore = targetScore;
        this.team1Score = team1Score;
        this.team2Score = team2Score;
        this.status = status;
    }

    public static MiniGame create(String title,
                                  String team1Member1,
                                  String team1Member2,
                                  String team2Member1,
                                  String team2Member2,
                                  Integer targetScore) {
        return MiniGame.builder()
                .title(title)
                .team1Member1(team1Member1)
                .team1Member2(team1Member2)
                .team2Member1(team2Member1)
                .team2Member2(team2Member2)
                .targetScore(targetScore)
                .team1Score(INITIAL_SCORE)
                .team2Score(INITIAL_SCORE)
                .status(MiniGameStatus.IN_PROGRESS)
                .build();
    }

    public void updateScore(Integer team1Score, Integer team2Score) {
        validateScore(team1Score, targetScore);
        validateScore(team2Score, targetScore);
        this.team1Score = team1Score;
        this.team2Score = team2Score;
    }

    private void validateTargetScore(Integer targetScore) {
        if (targetScore == null || !ALLOWED_TARGET_SCORES.contains(targetScore)) {
            throw new YbcException(ExceptionCode.INVALID_MINI_GAME_TARGET_SCORE);
        }
    }

    private void validateScore(Integer score, int targetScore) {
        if (score == null || score < 0 || score > targetScore) {
            throw new YbcException(ExceptionCode.INVALID_MINI_GAME_SCORE);
        }
    }
}
