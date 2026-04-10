package com.ybc.ybc.vote.repository.projection;

public record VoteAttendanceCountProjection(
        Long voteId,
        long attendeeCount
) {
}
