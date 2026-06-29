package com.ybc.ybc.vote.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ybc.ybc.vote.domain.Vote;

import java.time.DayOfWeek;
import java.time.LocalDate;

public record AdminCompletedVoteResponse(
        Long voteId,
        String title,
        String activityDay,
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate activityDate,
        String location,
        AdminVoteAttendanceResponse attendance,
        boolean matchRegistered,
        Long matchId
) {
    public static AdminCompletedVoteResponse from(Vote vote,
                                                  long attendeeCount,
                                                  long guestCount,
                                                  boolean matchRegistered,
                                                  Long matchId) {
        return new AdminCompletedVoteResponse(
                vote.getId(),
                vote.getTitle(),
                toKoreanDayOfWeek(vote.getActivityDate().getDayOfWeek()),
                vote.getActivityDate(),
                vote.getLocation(),
                AdminVoteAttendanceResponse.of(attendeeCount, guestCount),
                matchRegistered,
                matchId
        );
    }

    private static String toKoreanDayOfWeek(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> "월요일";
            case TUESDAY -> "화요일";
            case WEDNESDAY -> "수요일";
            case THURSDAY -> "목요일";
            case FRIDAY -> "금요일";
            case SATURDAY -> "토요일";
            case SUNDAY -> "일요일";
        };
    }
}
