package com.ybc.ybc.applications.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ybc.ybc.applications.domain.Application;
import com.ybc.ybc.global.util.PhoneNumberUtils;

import java.time.LocalDateTime;
import java.util.List;

public record ApplicantDetailResponse(
        Long applicationId,
        String name,
        String term,
        boolean isReapplicant,
        String address,
        String gender,
        int age,
        String phone,
        String university,
        String major,
        String introduction,
        String motivation,
        String equipment,
        List<String> interviewAvailableTimes,
        String discoverySource,
        String discoveryEtc,
        boolean wantsStaff,
        String finalComment,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime createdAt,
        String status,
        String email
) {
    public static ApplicantDetailResponse from(Application application, boolean isReapplicant) {
        return new ApplicantDetailResponse(
                application.getId(),
                application.getName(),
                application.getTerm(),
                isReapplicant,
                application.getAddress(),
                application.getGender().getDisplayName(),
                application.getAge(),
                PhoneNumberUtils.format(application.getPhone()),
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
