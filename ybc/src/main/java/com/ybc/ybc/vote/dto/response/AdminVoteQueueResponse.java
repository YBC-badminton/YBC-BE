package com.ybc.ybc.vote.dto.response;

import java.util.List;

public record AdminVoteQueueResponse(
        int count,
        List<AdminVoteQueueItemResponse> data
) {
}
