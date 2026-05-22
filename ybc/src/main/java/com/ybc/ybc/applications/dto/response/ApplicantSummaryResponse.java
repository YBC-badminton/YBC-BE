package com.ybc.ybc.applications.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ybc.ybc.applications.domain.Application;
import com.ybc.ybc.global.util.PhoneNumberUtils;

import java.time.LocalDateTime;

public record ApplicantSummaryResponse(
        Long applicationId,
        String name,
        String term,
        boolean isReapplicant,
        String gender,
        int age,
        String university,
        String major,
        String phone,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime createdAt,
        String status
) {
    public static ApplicantSummaryResponse from(Application application, boolean isReapplicant) {
        return new ApplicantSummaryResponse(
                application.getId(),
                application.getName(),
                application.getTerm(),
                isReapplicant,
                application.getGender() != null ? application.getGender().getDisplayName() : null,
                application.getAge(),
                application.getUniversity(),
                application.getMajor(),
                PhoneNumberUtils.format(application.getPhone()),
                application.getCreatedAt(),
                application.getStatusOrDefault().name()
        );
    }
}
