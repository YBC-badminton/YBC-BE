package com.ybc.ybc.vote.repository;

import com.ybc.ybc.vote.domain.VoteMatchGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VoteMatchGroupRepository extends JpaRepository<VoteMatchGroup, Long> {
    Optional<VoteMatchGroup> findByVote_Id(Long voteId);

    void deleteAllByVote_Id(Long voteId);
}
