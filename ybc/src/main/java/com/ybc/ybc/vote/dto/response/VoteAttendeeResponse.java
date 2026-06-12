package com.ybc.ybc.vote.dto.response;

import com.ybc.ybc.vote.domain.VoteAttendance;

public record VoteAttendeeResponse(
        Long memberId,
        String nickname,
        boolean isWaiting
) {
    public static VoteAttendeeResponse from(VoteAttendance voteAttendance, boolean isWaiting) {
        return new VoteAttendeeResponse(
                voteAttendance.getMember().getId(),
                voteAttendance.getMember().getNickname(),
                isWaiting
        );
    }
}
