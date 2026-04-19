package com.ybc.ybc.vote.dto.response;

import java.util.List;

public record AdminMatchGameResponse(
        Integer matchNumber,
        List<String> team1,
        List<String> team2
) {
}
