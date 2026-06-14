package com.ybc.ybc.vote.dto.response;

import java.util.List;

public record RecentVoteListResponse(
        int count,
        List<RecentVoteResponse> votes
) {
    public static RecentVoteListResponse from(List<RecentVoteResponse> votes) {
        return new RecentVoteListResponse(votes.size(), votes);
    }
}
