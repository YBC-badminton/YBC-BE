package com.ybc.ybc.vote.dto.response;

import java.util.List;

public record AdminVoteListResponse(
        AdminVoteListSummaryResponse summary,
        List<AdminVoteListItemResponse> data
) {
}
