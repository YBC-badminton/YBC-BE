package com.ybc.ybc.members.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ClubMemberCreateRequest(
        @NotBlank(message = "이름을 입력해 주세요.")
        String name,
        @NotBlank(message = "학교를 입력해 주세요.")
        String university,
        @NotBlank(message = "전화번호를 입력해 주세요.")
        String phone,
        @NotBlank(message = "출생년도를 입력해 주세요.")
        @Pattern(regexp = "\\d{2}", message = "출생년도 두 자리를 입력해 주세요.")
        String age,
        @NotBlank(message = "기수를 입력해 주세요.")
        String term,
        @Min(value = 1, message = "코트 번호를 입력해 주세요.")
        int court
) {
}
