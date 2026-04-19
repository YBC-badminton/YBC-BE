package com.ybc.ybc.vote.repository;

import com.ybc.ybc.vote.domain.VoteMatch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoteMatchRepository extends JpaRepository<VoteMatch, Long> {
    void deleteAllByVote_Id(Long voteId);
}
