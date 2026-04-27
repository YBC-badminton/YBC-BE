package com.ybc.ybc.vote.repository;

import com.ybc.ybc.vote.domain.VoteAttendance;
import com.ybc.ybc.vote.repository.projection.VoteAttendanceCountProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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

    List<VoteAttendance> findAllByVote_IdAndAttendanceStatusTrueOrderByIdAsc(Long voteId);

    void deleteAllByVote_Id(Long voteId);

    @Query("""
            select new com.ybc.ybc.vote.repository.projection.VoteAttendanceCountProjection(
                va.vote.id,
                count(va)
            )
            from VoteAttendance va
            where va.vote.id in :voteIds
              and va.attendanceStatus = true
            group by va.vote.id
            """)
    List<VoteAttendanceCountProjection> countAttendancesByVoteIds(@Param("voteIds") List<Long> voteIds);
}
