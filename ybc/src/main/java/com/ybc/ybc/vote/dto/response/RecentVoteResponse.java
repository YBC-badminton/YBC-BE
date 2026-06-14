package com.ybc.ybc.vote.dto.response;

import com.ybc.ybc.vote.domain.ActivityType;
import com.ybc.ybc.vote.domain.Vote;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record RecentVoteResponse(
        Long voteId,
        String name,
        ActivityType type,
        LocalDate activityDate,
        String location,
        LocalDateTime voteStartAt,
        LocalDateTime voteEndAt,
        String activityTime,
        int capacity,
        Long currentParticipantCount,
        boolean participantCountVisible
) {
    public static RecentVoteResponse from(Vote vote, Long currentParticipantCount, boolean participantCountVisible) {
        return new RecentVoteResponse(
                vote.getId(),
                vote.getTitle(),
                vote.getActivityType(),
                vote.getActivityDate(),
                vote.getLocation(),
                vote.getVoteStartAt(),
                vote.getVoteEndAt(),
                vote.getActivityTime(),
                vote.getCapacity(),
                currentParticipantCount,
                participantCountVisible
        );
    }
}
