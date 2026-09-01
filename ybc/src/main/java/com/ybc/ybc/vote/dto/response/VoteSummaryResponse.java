package com.ybc.ybc.vote.dto.response;

import com.ybc.ybc.vote.domain.ActivityType;
import com.ybc.ybc.vote.repository.projection.VoteSummaryProjection;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record VoteSummaryResponse(
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
        boolean matchRegistered,
        AdminVoteStatus status
) {
    public static VoteSummaryResponse from(VoteSummaryProjection projection, AdminVoteStatus status) {
        return new VoteSummaryResponse(
                projection.voteId(),
                projection.name(),
                projection.type(),
                projection.activityDate(),
                projection.location(),
                projection.voteStartAt(),
                projection.voteEndAt(),
                projection.activityTime(),
                projection.capacity(),
                projection.currentParticipantCount(),
                projection.matchRegistered(),
                status
        );
    }
}
