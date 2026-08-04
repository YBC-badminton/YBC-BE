package com.ybc.ybc.vote.dto.response;

import com.ybc.ybc.applications.domain.Gender;
import com.ybc.ybc.members.domain.ClubMemberCourt;

public record AdminMatchParticipantProfileResponse(
        String name,
        Gender gender,
        ClubMemberCourt court
) {
}
