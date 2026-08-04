package com.ybc.ybc.vote.dto.response;

import com.ybc.ybc.applications.domain.Gender;
import com.ybc.ybc.members.domain.ClubMemberCourt;
import com.ybc.ybc.vote.domain.MatchParticipantType;

public record AdminMatchDetailParticipantResponse(
        Long participantId,
        String name,
        Gender gender,
        ClubMemberCourt court,
        MatchParticipantType participantType
) {
}
