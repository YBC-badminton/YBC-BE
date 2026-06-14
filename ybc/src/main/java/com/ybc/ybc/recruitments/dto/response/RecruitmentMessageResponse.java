package com.ybc.ybc.recruitments.dto.response;

import com.ybc.ybc.recruitments.domain.Recruitment;

public record RecruitmentMessageResponse(
        boolean recruiting,
        String term,
        String recruitmentMessage
) {
    public static RecruitmentMessageResponse from(Recruitment recruitment) {
        return new RecruitmentMessageResponse(
                true,
                recruitment.getTerm(),
                recruitment.getRecruitmentMessage()
        );
    }

    public static RecruitmentMessageResponse closed() {
        return new RecruitmentMessageResponse(false, null, null);
    }
}
