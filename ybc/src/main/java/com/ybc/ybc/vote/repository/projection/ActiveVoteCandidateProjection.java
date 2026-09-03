package com.ybc.ybc.vote.repository.projection;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ActiveVoteCandidateProjection(
        Long voteId,
        String name,
        LocalDateTime voteStartAt,
        LocalDateTime voteEndAt,
        LocalDate activityDate,
        String activityTime
) {
}
