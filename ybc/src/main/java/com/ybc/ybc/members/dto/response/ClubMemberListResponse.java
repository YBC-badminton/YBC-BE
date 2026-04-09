package com.ybc.ybc.members.dto.response;

import java.util.List;

public record ClubMemberListResponse(
        ClubMemberSummaryResponse summary,
        List<ClubMemberResponse> members
) {
}
