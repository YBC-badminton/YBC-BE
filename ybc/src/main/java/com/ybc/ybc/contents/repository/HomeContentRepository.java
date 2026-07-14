package com.ybc.ybc.contents.repository;

import com.ybc.ybc.contents.domain.HomeContent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HomeContentRepository extends JpaRepository<HomeContent, Long> {
    Optional<HomeContent> findFirstByOrderByIdAsc();
}
