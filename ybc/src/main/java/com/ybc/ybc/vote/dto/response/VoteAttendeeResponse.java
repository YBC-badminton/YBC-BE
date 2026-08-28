package com.ybc.ybc.vote.dto.response;

import com.ybc.ybc.vote.domain.VoteAttendance;

import java.time.LocalDateTime;

public record VoteAttendeeResponse(
        int attendeeNumber,
        Long memberId,
        String nickname,
        LocalDateTime votedAt,
        boolean isWaiting,
        boolean isAdmin
) {
    public static VoteAttendeeResponse from(int attendeeNumber, VoteAttendance voteAttendance, boolean isWaiting) {
        return from(attendeeNumber, voteAttendance, voteAttendance.getMember().getNickname(), isWaiting);
    }

    public static VoteAttendeeResponse from(int attendeeNumber,
                                            VoteAttendance voteAttendance,
                                            String displayName,
                                            boolean isWaiting) {
        return new VoteAttendeeResponse(
                attendeeNumber,
                voteAttendance.getMember().getId(),
                displayName,
                resolveVotedAt(voteAttendance),
                isWaiting,
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
