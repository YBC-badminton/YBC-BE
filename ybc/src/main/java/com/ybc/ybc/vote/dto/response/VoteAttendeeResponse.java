package com.ybc.ybc.vote.dto.response;

import com.ybc.ybc.vote.domain.VoteAttendance;

public record VoteAttendeeResponse(
        int attendeeNumber,
        Long memberId,
        String nickname,
        boolean isWaiting
) {
    public static VoteAttendeeResponse from(int attendeeNumber, VoteAttendance voteAttendance, boolean isWaiting) {
        return new VoteAttendeeResponse(
                attendeeNumber,
                voteAttendance.getMember().getId(),
                voteAttendance.getMember().getNickname(),
                isWaiting
        );
    }
}
