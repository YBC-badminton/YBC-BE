package com.ybc.ybc.vote.dto.response;

import com.ybc.ybc.vote.domain.VoteAttendance;

public record VoteAttendeeResponse(
        Long memberId,
        String nickname
) {
    public static VoteAttendeeResponse from(VoteAttendance voteAttendance) {
        return new VoteAttendeeResponse(
                voteAttendance.getMember().getId(),
                voteAttendance.getMember().getNickname()
        );
    }
}
