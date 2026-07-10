package com.ybc.ybc.vote.repository;

import com.ybc.ybc.vote.domain.VoteAttendance;
import com.ybc.ybc.vote.repository.projection.VoteAttendanceCountProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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

    boolean existsByVote_IdInAndMember_IdAndAttendanceStatusTrue(Collection<Long> voteIds, Long memberId);

    List<VoteAttendance> findAllByVote_IdAndAttendanceStatusTrueOrderByIdAsc(Long voteId);

    @Query("""
            select va
            from VoteAttendance va
            join fetch va.member
            where va.vote.id = :voteId
              and va.attendanceStatus = false
            order by va.id
            """)
    List<VoteAttendance> findAllInactiveWithMemberByVoteId(@Param("voteId") Long voteId);

    void deleteAllByVote_Id(Long voteId);

    @Query("""
            select va
            from VoteAttendance va
            join fetch va.member
            where va.vote.id = :voteId
              and va.attendanceStatus = true
            order by va.id
            """)
    List<VoteAttendance> findAllActiveWithMemberByVoteId(@Param("voteId") Long voteId);

    @Query("""
            select va
            from VoteAttendance va
            join fetch va.member
            join fetch va.vote
            where va.vote.id in :voteIds
              and va.attendanceStatus = true
            order by va.vote.id, va.id
            """)
    List<VoteAttendance> findAllActiveWithMemberByVoteIds(@Param("voteIds") Collection<Long> voteIds);

    @Query("""
            select va
            from VoteAttendance va
            join fetch va.vote
            join fetch va.member
            where va.id in :ids
            """)
    List<VoteAttendance> findAllWithVoteAndMemberByIdIn(@Param("ids") Collection<Long> ids);

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
