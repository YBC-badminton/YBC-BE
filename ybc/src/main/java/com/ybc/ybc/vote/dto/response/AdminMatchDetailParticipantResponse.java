package com.ybc.ybc.vote.dto.response;

import com.ybc.ybc.applications.domain.Gender;
import com.ybc.ybc.vote.domain.MatchParticipantType;

public record AdminMatchDetailParticipantResponse(
        Long participantId,
        String name,
        Gender gender,
        MatchParticipantType participantType
) {
}
