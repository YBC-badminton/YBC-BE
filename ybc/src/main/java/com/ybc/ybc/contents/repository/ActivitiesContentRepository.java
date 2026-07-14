package com.ybc.ybc.contents.repository;

import com.ybc.ybc.contents.domain.ActivitiesContent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ActivitiesContentRepository extends JpaRepository<ActivitiesContent, Long> {
    Optional<ActivitiesContent> findFirstByOrderByIdAsc();
}
