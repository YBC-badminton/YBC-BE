package com.ybc.ybc.vote.dto.response;

import java.util.List;

public record VoteAttendeeListResponse(
        long totalAttendeeCount,
        List<VoteAttendeeResponse> attendees
) {
    public static VoteAttendeeListResponse from(List<VoteAttendeeResponse> attendees) {
        return new VoteAttendeeListResponse(attendees.size(), attendees);
    }
}
