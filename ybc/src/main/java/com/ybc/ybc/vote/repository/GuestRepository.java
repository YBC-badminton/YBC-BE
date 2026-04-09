package com.ybc.ybc.vote.repository;

import com.ybc.ybc.vote.domain.Guest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GuestRepository extends JpaRepository<Guest, Long> {
    long countByVote_Id(Long voteId);

    List<Guest> findAllByVote_IdOrderByIdAsc(Long voteId);
}
