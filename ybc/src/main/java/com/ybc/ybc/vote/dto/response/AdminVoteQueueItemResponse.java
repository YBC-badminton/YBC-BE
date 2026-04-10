package com.ybc.ybc.vote.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ybc.ybc.vote.domain.ActivityType;
import com.ybc.ybc.vote.domain.Vote;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AdminVoteQueueItemResponse(
        Long voteId,
        ActivityType activityType,
        String title,
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate activityDate,
        String activityTime,
        String location,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime voteStartAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime voteEndAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime createdAt
) {
    public static AdminVoteQueueItemResponse from(Vote vote) {
        return new AdminVoteQueueItemResponse(
                vote.getId(),
                vote.getActivityType(),
                vote.getTitle(),
                vote.getActivityDate(),
                vote.getActivityTime(),
                vote.getLocation(),
                vote.getVoteStartAt(),
                vote.getVoteEndAt(),
                vote.getCreatedAt()
        );
    }
}
