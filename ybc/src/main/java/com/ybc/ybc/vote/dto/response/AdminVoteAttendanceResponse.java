package com.ybc.ybc.vote.dto.response;

public record AdminVoteAttendanceResponse(
        long currentAttendees,
        long currentGuests,
        long totalParticipants
) {
    public static AdminVoteAttendanceResponse of(long currentAttendees, long currentGuests) {
        return new AdminVoteAttendanceResponse(
                currentAttendees,
                currentGuests,
                currentAttendees
        );
    }
}
