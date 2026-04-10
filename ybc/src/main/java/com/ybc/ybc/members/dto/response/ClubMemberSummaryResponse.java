package com.ybc.ybc.members.dto.response;

public record ClubMemberSummaryResponse(
        long totalMembers,
        long court1,
        long court2,
        long court3,
        long court4
) {
}
