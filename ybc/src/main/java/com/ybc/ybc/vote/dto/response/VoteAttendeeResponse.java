package com.ybc.ybc.vote.dto.response;

import com.ybc.ybc.vote.domain.VoteAttendance;

import java.time.LocalDateTime;

public record VoteAttendeeResponse(
        int attendeeNumber,
        Long memberId,
        String nickname,
        LocalDateTime votedAt,
        boolean isWaiting
) {
    public static VoteAttendeeResponse from(int attendeeNumber, VoteAttendance voteAttendance, boolean isWaiting) {
        return new VoteAttendeeResponse(
                attendeeNumber,
                voteAttendance.getMember().getId(),
                voteAttendance.getMember().getNickname(),
                resolveVotedAt(voteAttendance),
                isWaiting
        );
    }

    private static LocalDateTime resolveVotedAt(VoteAttendance voteAttendance) {
        if (voteAttendance.getVotedAt() != null) {
            return voteAttendance.getVotedAt();
        }
        return voteAttendance.getCreatedAt();
    }
}
