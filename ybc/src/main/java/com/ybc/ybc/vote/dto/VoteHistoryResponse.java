package com.ybc.ybc.vote.dto;

public record VoteHistoryResponse(
        long joinableVotes,
        long thisYearVotes
) {
}
