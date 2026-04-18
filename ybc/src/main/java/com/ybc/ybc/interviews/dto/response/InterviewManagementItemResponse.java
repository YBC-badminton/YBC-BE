package com.ybc.ybc.interviews.dto.response;

import com.ybc.ybc.applications.domain.Application;
import com.ybc.ybc.interviews.domain.Interview;

import java.util.List;

public record InterviewManagementItemResponse(
        Long applicationId,
        String name,
        List<String> availableSessions,
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
