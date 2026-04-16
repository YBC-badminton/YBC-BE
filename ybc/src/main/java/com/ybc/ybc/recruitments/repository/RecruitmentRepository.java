package com.ybc.ybc.recruitments.repository;

import com.ybc.ybc.recruitments.domain.Recruitment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecruitmentRepository extends JpaRepository<Recruitment, Long> {
}
