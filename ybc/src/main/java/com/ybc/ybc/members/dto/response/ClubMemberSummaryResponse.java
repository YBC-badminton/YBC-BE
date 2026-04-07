package com.ybc.ybc.members.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ClubMemberSummaryResponse(
        @JsonProperty("total_members")
        long totalMembers,
        @JsonProperty("court_1")
        long court1,
        @JsonProperty("court_2")
        long court2,
        @JsonProperty("court_3")
        long court3,
        @JsonProperty("court_4")
        long court4
) {
}
