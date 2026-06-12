package com.ybc.ybc.vote.repository;

import com.ybc.ybc.vote.domain.Guest;
import com.ybc.ybc.vote.repository.projection.VoteGuestCountProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GuestRepository extends JpaRepository<Guest, Long> {
    long countByVote_Id(Long voteId);

    List<Guest> findAllByVote_IdOrderByIdAsc(Long voteId);

    List<Guest> findAllByVote_IdInOrderByIdAsc(Collection<Long> voteIds);

    Optional<Guest> findByIdAndVote_IdIn(Long guestId, Collection<Long> voteIds);

    List<Guest> findAllByVote_IdInOrderByVote_IdAscIdAsc(Collection<Long> voteIds);

    @Query("""
            select g
            from Guest g
            join fetch g.vote
            where g.id in :ids
            """)
    List<Guest> findAllWithVoteByIdIn(@Param("ids") Collection<Long> ids);

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
