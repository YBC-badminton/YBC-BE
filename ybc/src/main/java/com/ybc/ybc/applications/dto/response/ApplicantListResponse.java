package com.ybc.ybc.applications.dto.response;

import java.util.List;

public record ApplicantListResponse(
        List<ApplicantSummaryResponse> applicants,
        long totalCount
) {
}
