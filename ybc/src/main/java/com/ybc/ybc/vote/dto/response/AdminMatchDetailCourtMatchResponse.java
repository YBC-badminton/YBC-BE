package com.ybc.ybc.vote.dto.response;

import java.util.List;

public record AdminMatchDetailCourtMatchResponse(
        Integer matchNumber,
        List<AdminMatchDetailParticipantResponse> team1,
        List<AdminMatchDetailParticipantResponse> team2
) {
}
