package com.ybc.ybc.vote.repository;

import com.ybc.ybc.vote.domain.VoteAttendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface VoteAttendanceRepository extends JpaRepository<VoteAttendance, Long> {
    @Query("""
            select count(va)
            from VoteAttendance va
            where va.vote.id = :voteId
              and va.attendanceStatus = true
            """)
    long countAttendancesByVoteId(@Param("voteId") Long voteId);

    Optional<VoteAttendance> findByVote_IdAndMember_Id(Long voteId, Long memberId);
}
