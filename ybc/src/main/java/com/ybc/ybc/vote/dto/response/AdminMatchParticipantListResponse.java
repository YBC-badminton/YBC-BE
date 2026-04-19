package com.ybc.ybc.vote.dto.response;

import java.util.List;

public record AdminMatchParticipantListResponse(
        Long voteId,
        int totalCount,
        List<AdminMatchParticipantResponse> participants
) {
}
