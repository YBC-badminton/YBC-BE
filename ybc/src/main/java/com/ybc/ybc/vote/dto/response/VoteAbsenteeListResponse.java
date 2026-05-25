package com.ybc.ybc.vote.dto.response;

import com.ybc.ybc.vote.domain.VoteAttendance;

import java.util.List;

public record VoteAbsenteeListResponse(
        long totalAbsenteeCount,
        List<VoteAttendeeResponse> absentees
) {
    public static VoteAbsenteeListResponse from(List<VoteAttendance> voteAttendances) {
        List<VoteAttendeeResponse> absentees = voteAttendances.stream()
                .map(VoteAttendeeResponse::from)
                .toList();
        return new VoteAbsenteeListResponse(absentees.size(), absentees);
    }
}
