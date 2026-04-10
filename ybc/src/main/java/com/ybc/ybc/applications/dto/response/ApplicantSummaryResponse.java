package com.ybc.ybc.applications.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ybc.ybc.applications.domain.Application;

import java.time.LocalDateTime;

public record ApplicantSummaryResponse(
        Long applicationId,
        String name,
        String gender,
        int age,
        String university,
        String major,
        String phone,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime createdAt,
        String status
) {
    public static ApplicantSummaryResponse from(Application application) {
        return new ApplicantSummaryResponse(
                application.getId(),
                application.getName(),
                application.getGender().getDisplayName(),
                application.getAge(),
                application.getUniversity(),
                application.getMajor(),
                application.getPhone(),
                application.getCreatedAt(),
                application.getStatusOrDefault().name()
        );
    }
}
