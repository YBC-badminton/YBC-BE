package com.ybc.ybc.recruitments.dto.response;

import com.ybc.ybc.recruitments.domain.Recruitment;

import java.time.LocalDate;

public record RecruitmentInterviewTimesResponse(
        boolean recruiting,
        String term,
        LocalDate interviewDate,
        String interviewFirstTime,
        String interviewSecondTime
) {
    public static RecruitmentInterviewTimesResponse from(Recruitment recruitment) {
        return new RecruitmentInterviewTimesResponse(
                true,
                recruitment.getTerm(),
                recruitment.getInterviewDate(),
                recruitment.getInterviewFirstTime(),
                recruitment.getInterviewSecondTime()
        );
    }

    public static RecruitmentInterviewTimesResponse closed() {
        return new RecruitmentInterviewTimesResponse(false, null, null, null, null);
    }
}
