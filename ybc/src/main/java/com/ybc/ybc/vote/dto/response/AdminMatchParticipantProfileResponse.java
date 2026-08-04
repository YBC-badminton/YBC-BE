package com.ybc.ybc.vote.dto.response;

import com.ybc.ybc.applications.domain.Gender;

public record AdminMatchParticipantProfileResponse(
        String name,
        Gender gender,
        String level
) {
}
