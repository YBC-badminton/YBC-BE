package com.ybc.ybc.minigames.repository;

import com.ybc.ybc.minigames.domain.MiniGame;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;

public interface MiniGameRepository extends JpaRepository<MiniGame, Long> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from MiniGame m
            where (m.gameDate is not null and m.gameDate <= :gameDateCutoff)
               or (m.gameDate is null and m.createdAt <= :createdAtCutoff)
            """)
    int deleteExpiredMiniGames(@Param("gameDateCutoff") LocalDate gameDateCutoff,
                               @Param("createdAtCutoff") LocalDateTime createdAtCutoff);
}
