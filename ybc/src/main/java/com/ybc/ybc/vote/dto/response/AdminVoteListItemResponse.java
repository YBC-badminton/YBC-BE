package com.ybc.ybc.vote.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ybc.ybc.vote.domain.ActivityType;
import com.ybc.ybc.vote.domain.Vote;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AdminVoteListItemResponse(
        Long voteId,
        AdminVoteStatus status,
        ActivityType activityType,
        String title,
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate activityDate,
        String activityTime,
        String location,
        String memo,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime voteStartAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime voteEndAt,
        int capacity,
        AdminVoteAttendanceResponse attendance
) {
    public static AdminVoteListItemResponse from(Vote vote,
                                                 AdminVoteStatus status,
                                                 long attendeeCount,
                                                 long guestCount) {
        return new AdminVoteListItemResponse(
                vote.getId(),
                status,
                vote.getActivityType(),
                vote.getTitle(),
                vote.getActivityDate(),
                vote.getActivityTime(),
                vote.getLocation(),
                vote.getMemo(),
                vote.getVoteStartAt(),
                vote.getVoteEndAt(),
                vote.getCapacity(),
                AdminVoteAttendanceResponse.of(attendeeCount, guestCount)
        );
    }
}
