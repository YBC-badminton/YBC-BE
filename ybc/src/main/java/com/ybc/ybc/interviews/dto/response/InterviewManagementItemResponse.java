package com.ybc.ybc.interviews.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ybc.ybc.applications.domain.Application;
import com.ybc.ybc.interviews.domain.Interview;

import java.util.List;

public record InterviewManagementItemResponse(
        @JsonProperty("application_id")
        Long applicationId,
        String name,
        @JsonProperty("available_sessions")
        List<String> availableSessions,
        @JsonProperty("scheduled_at")
        String scheduledAt,
        String status,
        String memo
) {
    public static InterviewManagementItemResponse from(Application application, Interview interview) {
        return new InterviewManagementItemResponse(
                application.getId(),
                application.getName(),
                application.getInterviewTimes().stream()
                        .map(Enum::name)
                        .toList(),
                interview != null && interview.getScheduledAt() != null
                        ? interview.getScheduledAt().name()
                        : null,
                application.getStatusOrDefault().name(),
                interview != null ? interview.getMemo() : null
        );
    }
}
