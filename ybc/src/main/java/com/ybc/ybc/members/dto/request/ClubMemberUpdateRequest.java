package com.ybc.ybc.members.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ClubMemberUpdateRequest(
        @NotBlank(message = "이름을 입력해 주세요.")
        String name,
        @Min(value = 1, message = "나이는 1 이상이어야 합니다.")
        int age,
        @NotBlank(message = "학교를 입력해 주세요.")
        String university,
        @NotBlank(message = "기수를 입력해 주세요.")
        String term,
        @Min(value = 1, message = "코트 번호를 입력해 주세요.")
        int court
) {
}
