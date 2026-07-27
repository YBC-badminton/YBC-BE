package com.ybc.ybc.vote.dto.response;

import java.util.List;

public record AdminMatchDetailCourtResponse(
        Integer courtNumber,
        List<AdminMatchDetailCourtMatchResponse> matches
) {
}
