package com.ybc.ybc.vote.repository;

import com.ybc.ybc.vote.domain.VoteAttendance;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoteAttendanceRepository extends JpaRepository<VoteAttendance, Long> {
}
