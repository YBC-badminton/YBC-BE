package com.ybc.ybc.vote.dto.response;

import com.ybc.ybc.vote.domain.ActivityType;
import com.ybc.ybc.vote.domain.Vote;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record VoteDetailResponse(
        Long voteId,
        String name,
        ActivityType type,
        LocalDate activityDate,
        String activityTime,
        String location,
        String memo,
        LocalDateTime voteStartAt,
        LocalDateTime voteEndAt,
        int capacity,
        long currentParticipantCount
) {
    public static VoteDetailResponse from(Vote vote, long currentParticipantCount) {
        return new VoteDetailResponse(
                vote.getId(),
                vote.getTitle(),
                vote.getActivityType(),
                vote.getActivityDate(),
                vote.getActivityTime(),
                vote.getLocation(),
                vote.getMemo(),
                vote.getVoteStartAt(),
                vote.getVoteEndAt(),
                vote.getCapacity(),
                currentParticipantCount
        );
    }
}
