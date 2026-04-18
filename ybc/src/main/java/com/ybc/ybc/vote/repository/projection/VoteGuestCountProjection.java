package com.ybc.ybc.vote.repository.projection;

public record VoteGuestCountProjection(
        Long voteId,
        long guestCount
) {
}
