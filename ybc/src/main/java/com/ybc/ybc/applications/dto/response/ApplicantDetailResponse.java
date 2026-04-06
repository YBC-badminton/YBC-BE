package com.ybc.ybc.applications.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ybc.ybc.applications.domain.Application;

import java.time.LocalDateTime;
import java.util.List;

public record ApplicantDetailResponse(
        @JsonProperty("application_id")
        Long applicationId,
        String name,
        String address,
        String gender,
        int age,
        String phone,
        String university,
        String major,
        String introduction,
        String motivation,
        String equipment,
        @JsonProperty("interview_available_times")
        List<String> interviewAvailableTimes,
        @JsonProperty("discovery_source")
        String discoverySource,
        @JsonProperty("discovery_etc")
        String discoveryEtc,
        @JsonProperty("wants_staff")
        boolean wantsStaff,
        @JsonProperty("final_comment")
        String finalComment,
        @JsonProperty("created_at")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime createdAt,
        String status,
        String email
) {
    public static ApplicantDetailResponse from(Application application) {
        return new ApplicantDetailResponse(
                application.getId(),
                application.getName(),
                application.getAddress(),
                application.getGender().getDisplayName(),
                application.getAge(),
                application.getPhone(),
                application.getUniversity(),
                application.getMajor(),
                application.getIntroduction(),
                application.getMotivation(),
                application.getEquipment(),
                application.getInterviewTimes()
                        .stream()
                        .map(interviewTime -> interviewTime.getDisplayName())
                        .toList(),
                application.getDiscoverySource().getDisplayName(),
                application.getDiscoveryEtc(),
                application.isWantsStaff(),
                application.getFinalComment(),
                application.getCreatedAt(),
                application.getStatusOrDefault().name(),
                application.getEmail()
        );
    }
}
