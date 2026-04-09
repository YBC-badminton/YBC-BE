package com.ybc.ybc.applications.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ApplicantListResponse(
        List<ApplicantSummaryResponse> applicants,
        @JsonProperty("total_count")
        long totalCount
) {
}
