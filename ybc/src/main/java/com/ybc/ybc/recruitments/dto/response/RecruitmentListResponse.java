package com.ybc.ybc.recruitments.dto.response;

import java.util.List;

public record RecruitmentListResponse(
        List<RecruitmentSummaryResponse> recruitments
) {
}
