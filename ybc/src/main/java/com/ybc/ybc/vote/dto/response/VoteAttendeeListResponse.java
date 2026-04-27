package com.ybc.ybc.vote.dto.response;

import com.ybc.ybc.vote.domain.VoteAttendance;

import java.util.List;

public record VoteAttendeeListResponse(
        long totalAttendeeCount,
        List<VoteAttendeeResponse> attendees
) {
    public static VoteAttendeeListResponse from(List<VoteAttendance> voteAttendances) {
        List<VoteAttendeeResponse> attendees = voteAttendances.stream()
                .map(VoteAttendeeResponse::from)
                .toList();
        return new VoteAttendeeListResponse(attendees.size(), attendees);
    }
}
