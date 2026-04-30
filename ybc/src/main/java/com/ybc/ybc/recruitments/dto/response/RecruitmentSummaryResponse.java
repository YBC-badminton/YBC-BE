package com.ybc.ybc.recruitments.dto.response;

import com.ybc.ybc.recruitments.domain.Recruitment;

public record RecruitmentSummaryResponse(
        Long recruitmentId,
        String term
) {
    public static RecruitmentSummaryResponse from(Recruitment recruitment) {
        return new RecruitmentSummaryResponse(
                recruitment.getId(),
                recruitment.getTerm()
        );
    }
}
