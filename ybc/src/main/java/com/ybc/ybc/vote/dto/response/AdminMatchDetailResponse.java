package com.ybc.ybc.vote.dto.response;

import java.util.List;

public record AdminMatchDetailResponse(
        Long voteId,
        Long matchId,
        List<AdminMatchDetailCourtMatchResponse> courtMatches
) {
}
