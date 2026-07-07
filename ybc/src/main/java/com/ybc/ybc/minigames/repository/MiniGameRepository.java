package com.ybc.ybc.minigames.repository;

import com.ybc.ybc.minigames.domain.MiniGame;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MiniGameRepository extends JpaRepository<MiniGame, Long> {
}
