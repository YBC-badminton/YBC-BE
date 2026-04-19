package com.ybc.ybc.vote.dto.response;

import java.util.List;

public record AdminMatchGameResponse(
        Integer matchNumber,
        List<AdminMatchParticipantProfileResponse> team1,
        List<AdminMatchParticipantProfileResponse> team2
) {
}
