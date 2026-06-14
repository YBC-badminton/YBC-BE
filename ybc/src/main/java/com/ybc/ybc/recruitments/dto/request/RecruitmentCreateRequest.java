package com.ybc.ybc.recruitments.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record RecruitmentCreateRequest(
        @NotBlank(message = "기수를 입력해 주세요.")
        String term,
        @NotNull(message = "모집 시작일을 입력해 주세요.")
        LocalDateTime startAt,
        @NotNull(message = "모집 마감일을 입력해 주세요.")
        LocalDateTime endAt,
        @NotNull(message = "1차 합격자 발표일을 입력해 주세요.")
        LocalDate firstResultDate,
        @NotNull(message = "최종 합격자 발표일을 입력해 주세요.")
        LocalDate finalResultDate,
        @NotNull(message = "면접 날짜를 입력해 주세요.")
        LocalDate interviewDate,
        @NotBlank(message = "면접 장소를 입력해 주세요.")
        String interviewLocation,
        @NotBlank(message = "면접 1타임을 입력해 주세요.")
        String interviewFirstTime,
        @NotBlank(message = "면접 2타임을 입력해 주세요.")
        String interviewSecondTime,
        LocalDate otDate,
        String recruitmentMessage,
        @Positive(message = "회비는 1 이상이어야 합니다.")
        @NotNull(message = "회비를 입력해주세요")
        int membershipFee,
        @Positive(message = "활동 기간은 1 이상이어야 합니다.")
        @NotNull(message = "활동 기간을 입력해주세요")
        int activityPeriod
) {
}
