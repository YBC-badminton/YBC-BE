package com.ybc.ybc.vote.dto.response;

public record VoteHistoryResponse(
        long joinableVotes,
        long thisYearVotes
) {
}
