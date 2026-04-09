package com.ybc.ybc.vote.repository;

import com.ybc.ybc.vote.domain.VoteAttendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VoteAttendanceRepository extends JpaRepository<VoteAttendance, Long> {
    long countByVote_IdAndAttendanceStatusTrue(Long voteId);

    Optional<VoteAttendance> findByVote_IdAndMember_Id(Long voteId, Long memberId);
}
