package com.ybc.ybc.vote.repository;

import com.ybc.ybc.vote.domain.Guest;
import com.ybc.ybc.vote.repository.projection.VoteGuestCountProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GuestRepository extends JpaRepository<Guest, Long> {
    long countByVote_Id(Long voteId);

    List<Guest> findAllByVote_IdOrderByIdAsc(Long voteId);

    void deleteAllByVote_Id(Long voteId);

    @Query("""
            select new com.ybc.ybc.vote.repository.projection.VoteGuestCountProjection(
                g.vote.id,
                count(g)
            )
            from Guest g
            where g.vote.id in :voteIds
            group by g.vote.id
            """)
    List<VoteGuestCountProjection> countGuestsByVoteIds(@Param("voteIds") List<Long> voteIds);
}
