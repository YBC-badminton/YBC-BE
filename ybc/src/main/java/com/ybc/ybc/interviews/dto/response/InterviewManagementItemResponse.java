package com.ybc.ybc.interviews.dto.response;

import com.ybc.ybc.applications.domain.Application;
import com.ybc.ybc.interviews.domain.Interview;

import java.util.List;
import java.util.Objects;

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
                toAvailableSessions(application),
                interview != null && interview.getScheduledAt() != null
                        ? interview.getScheduledAt().name()
                        : null,
                application.getStatusOrDefault().name(),
                interview != null ? interview.getMemo() : null
        );
    }

    private static List<String> toAvailableSessions(Application application) {
        try {
            return application.getInterviewTimes().stream()
                    .filter(Objects::nonNull)
                    .map(Enum::name)
                    .toList();
        } catch (RuntimeException exception) {
            return List.of();
        }
    }
}
