package com.ybc.ybc.vote.dto;

import com.ybc.ybc.vote.domain.ActivityType;

import java.time.LocalDateTime;

public record VoteSummaryResponse(
        Long voteId,
        String name,
        ActivityType type,
        String location,
        LocalDateTime voteStartAt,
        LocalDateTime voteEndAt,
        String activityTime,
        int capacity,
        long currentParticipantCount
) {
}
