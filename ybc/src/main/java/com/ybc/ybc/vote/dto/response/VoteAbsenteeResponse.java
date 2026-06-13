package com.ybc.ybc.vote.dto.response;

import com.ybc.ybc.vote.domain.VoteAttendance;

public record VoteAbsenteeResponse(
        Long memberId,
        String nickname,
        boolean isWaiting
) {
    public static VoteAbsenteeResponse from(VoteAttendance voteAttendance) {
        return new VoteAbsenteeResponse(
                voteAttendance.getMember().getId(),
                voteAttendance.getMember().getNickname(),
                false
        );
    }
}
