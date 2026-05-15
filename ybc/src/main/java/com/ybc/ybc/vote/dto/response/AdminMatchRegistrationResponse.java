package com.ybc.ybc.vote.dto.response;

import java.util.List;

public record AdminMatchRegistrationResponse(
        Long voteId,
        Long matchId,
        int courtCount,
        List<AdminMatchCourtResponse> matches
) {
}
