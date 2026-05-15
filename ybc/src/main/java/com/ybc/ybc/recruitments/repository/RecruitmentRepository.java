package com.ybc.ybc.recruitments.repository;

import com.ybc.ybc.recruitments.domain.Recruitment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RecruitmentRepository extends JpaRepository<Recruitment, Long> {
    Optional<Recruitment> findFirstByStartAtLessThanEqualAndEndAtGreaterThanEqualOrderByStartAtDesc(LocalDateTime startAt,
                                                                                                     LocalDateTime endAt);
}
