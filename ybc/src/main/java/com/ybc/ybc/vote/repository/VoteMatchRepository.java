package com.ybc.ybc.vote.repository;

import com.ybc.ybc.vote.domain.VoteMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface VoteMatchRepository extends JpaRepository<VoteMatch, Long> {
    void deleteAllByVote_Id(Long voteId);

    void deleteAllByMatchGroup_IdAndCourtNumberAndMatchNumber(Long matchGroupId, Integer courtNumber, Integer matchNumber);

    @Query("""
            select vm
            from VoteMatch vm
            join fetch vm.matchGroup mg
            join fetch mg.vote
            join fetch vm.vote
            join fetch vm.voteAttendance va
            join fetch va.member
            where mg.id = :matchId
            order by vm.courtNumber asc, vm.matchNumber asc, vm.teamNumber asc, vm.id asc
            """)
    List<VoteMatch> findAllWithParticipantsByMatchGroupId(@Param("matchId") Long matchId);
}
