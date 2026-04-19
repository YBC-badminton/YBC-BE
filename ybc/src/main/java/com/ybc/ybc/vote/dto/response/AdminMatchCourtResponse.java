package com.ybc.ybc.vote.dto.response;

import java.util.List;

public record AdminMatchCourtResponse(
        Integer courtNumber,
        List<AdminMatchGameResponse> courtMatches
) {
}
