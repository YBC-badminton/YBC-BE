package com.ybc.ybc.vote.dto.response;

import com.ybc.ybc.vote.domain.VoteAttendance;

import java.util.List;

public record VoteAbsenteeListResponse(
        long totalAbsenteeCount,
        List<VoteAbsenteeResponse> absentees
) {
    public static VoteAbsenteeListResponse from(List<VoteAttendance> voteAttendances) {
        List<VoteAbsenteeResponse> absentees = voteAttendances.stream()
                .map(VoteAbsenteeResponse::from)
                .toList();
        return new VoteAbsenteeListResponse(absentees.size(), absentees);
    }
}
