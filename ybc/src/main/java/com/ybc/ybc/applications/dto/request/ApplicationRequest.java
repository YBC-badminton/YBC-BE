package com.ybc.ybc.applications.dto.request;

import com.ybc.ybc.applications.domain.Application;
import com.ybc.ybc.applications.domain.DiscoverySource;
import com.ybc.ybc.applications.domain.Gender;
import com.ybc.ybc.applications.domain.Status;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ApplicationRequest(
        @NotBlank(message = "이름을 입력해 주세요.")
        String name,
        @NotBlank(message = "주소를 입력해 주세요.")
        String address,
        @NotNull(message = "성별을 입력해 주세요.")
        Gender gender,
        int age,
        @NotBlank(message = "전화번호를 입력해 주세요.")
        String phone,
        @NotBlank(message = "학교를 입력해 주세요.")
        String university,
        @NotBlank(message = "학과를 입력해 주세요.")
        String major,
        @NotBlank(message = "자기소개를 입력해 주세요.")
        String introduction,
        @NotBlank(message = "지원동기를 입력해 주세요.")
        String motivation,
        String equipment,
        @NotNull(message = "면접 시간을 입력해 주세요.")
        List<String> interviewTimes,
        @NotNull(message = "유입 경로를 입력해 주세요.")
        DiscoverySource discoverySource,
        String discoveryEtc,
        boolean wantsStaff,
        String finalComment,
        Status status,
        @Email
        @NotBlank
        String email
) {

    public Application toApplication() {
        Application app = Application.builder()
                .name(name)
                .address(address)
                .gender(gender)
                .age(age)
                .phone(phone)
                .university(university)
                .major(major)
                .introduction(introduction)
                .motivation(motivation)
                .equipment(equipment)
                .discoverySource(discoverySource)
                .discoveryEtc(discoveryEtc)
                .wantsStaff(wantsStaff)
                .finalComment(finalComment)
                .status(status)
                .email(email)
                .build();

        for (String interviewTime : interviewTimes) {
            app.addInterviewTime(interviewTime);
        }

        return app;
    }
}
