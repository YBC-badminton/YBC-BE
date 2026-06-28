package com.ybc.ybc.vote.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ybc.ybc.vote.domain.ActivityType;
import com.ybc.ybc.vote.domain.Vote;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record FlashVoteCreateResponse(
        Long voteId,
        ActivityType activityType,
        String title,
        Long openedByMemberId,
        String openedByNickname,
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate activityDate,
        String activityTime,
        String location,
        String memo,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime voteStartAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime voteEndAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime createdAt
) {
    public static FlashVoteCreateResponse from(Vote vote) {
        return new FlashVoteCreateResponse(
                vote.getId(),
                vote.getActivityType(),
                vote.getTitle(),
                vote.getOpenedByMemberId(),
                vote.getOpenedByNickname(),
                vote.getActivityDate(),
                vote.getActivityTime(),
                vote.getLocation(),
                vote.getMemo(),
                vote.getVoteStartAt(),
                vote.getVoteEndAt(),
                vote.getCreatedAt()
        );
    }
}
