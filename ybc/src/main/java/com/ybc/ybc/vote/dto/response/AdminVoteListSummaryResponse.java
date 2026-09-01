package com.ybc.ybc.vote.dto.response;

public record AdminVoteListSummaryResponse(
        long totalCount,
        long upcomingCount,
        long inProgressCount,
        long activeCount,
        long completedCount
) {
}
