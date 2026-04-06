package com.ybc.ybc.applications.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ybc.ybc.applications.domain.Application;

import java.time.LocalDateTime;

public record ApplicantSummaryResponse(
        @JsonProperty("application_id")
        Long applicationId,
        String name,
        String gender,
        int age,
        String university,
        String major,
        String phone,
        @JsonProperty("created_at")
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
