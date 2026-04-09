package com.ybc.ybc.vote.repository;

import com.ybc.ybc.vote.domain.Guest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuestRepository extends JpaRepository<Guest, Long> {
    long countByVote_Id(Long voteId);
}
