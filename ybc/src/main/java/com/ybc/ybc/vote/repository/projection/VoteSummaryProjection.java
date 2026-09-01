package com.ybc.ybc.vote.repository.projection;

import com.ybc.ybc.vote.domain.ActivityType;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record VoteSummaryProjection(
        Long voteId,
        String name,
        ActivityType type,
        LocalDate activityDate,
        String location,
        LocalDateTime voteStartAt,
        LocalDateTime voteEndAt,
        String activityTime,
        int capacity,
        long currentParticipantCount,
        boolean matchRegistered
) {
}
