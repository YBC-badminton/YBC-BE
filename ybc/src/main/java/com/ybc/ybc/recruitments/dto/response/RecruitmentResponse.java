package com.ybc.ybc.recruitments.dto.response;

import com.ybc.ybc.recruitments.domain.Recruitment;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record RecruitmentResponse(
        Long recruitmentId,
        String term,
        LocalDateTime startAt,
        LocalDateTime endAt,
        LocalDate firstResultDate,
        LocalDate finalResultDate,
        LocalDate interviewDate,
        String interviewLocation,
        String interviewFirstTime,
        String interviewSecondTime,
        LocalDate otDate,
        String recruitmentMessage,
        int membershipFee,
        int activityPeriod
) {
    public static RecruitmentResponse from(Recruitment recruitment) {
        return new RecruitmentResponse(
                recruitment.getId(),
                recruitment.getTerm(),
                recruitment.getStartAt(),
                recruitment.getEndAt(),
                recruitment.getFirstResultDate(),
                recruitment.getFinalResultDate(),
                recruitment.getInterviewDate(),
                recruitment.getInterviewLocation(),
                recruitment.getInterviewFirstTime(),
                recruitment.getInterviewSecondTime(),
                recruitment.getOtDate(),
                recruitment.getRecruitmentMessage(),
                recruitment.getMembershipFee(),
                recruitment.getActivityPeriod()
        );
    }
}
