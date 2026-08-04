package com.ybc.ybc.members.dto.request;

import com.ybc.ybc.applications.domain.Gender;
import com.ybc.ybc.members.domain.ClubMemberCourt;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
        Boolean isMapoResident,
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email,
        @NotNull(message = "성별을 입력해 주세요.")
        Gender gender,
        ClubMemberCourt court
) {
    public ClubMemberCreateRequest(String name,
                                   String university,
                                   String phone,
                                   String age,
                                   String term) {
        this(name, university, phone, age, term, null, null, null, null);
    }

    public ClubMemberCreateRequest(String name,
                                   String university,
                                   String phone,
                                   String age,
                                   String term,
                                   String email,
                                   Gender gender) {
        this(name, university, phone, age, term, null, email, gender, null);
    }
}
