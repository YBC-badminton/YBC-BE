package com.ybc.ybc.vote.dto.response;

import com.ybc.ybc.vote.domain.VoteAttendance;

import java.time.LocalDateTime;

public record VoteAbsenteeResponse(
        Long memberId,
        String nickname,
        LocalDateTime votedAt,
        boolean isWaiting,
        boolean isAdmin
) {
    public static VoteAbsenteeResponse from(VoteAttendance voteAttendance) {
        return new VoteAbsenteeResponse(
                voteAttendance.getMember().getId(),
                voteAttendance.getMember().getNickname(),
                resolveVotedAt(voteAttendance),
                false,
                voteAttendance.getMember().isAdmin()
        );
    }

    private static LocalDateTime resolveVotedAt(VoteAttendance voteAttendance) {
        if (voteAttendance.getVotedAt() != null) {
            return voteAttendance.getVotedAt();
        }
        return voteAttendance.getCreatedAt();
    }
}
