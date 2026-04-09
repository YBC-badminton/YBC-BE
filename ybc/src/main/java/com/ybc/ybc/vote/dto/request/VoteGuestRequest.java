package com.ybc.ybc.vote.dto.request;

import com.ybc.ybc.applications.domain.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VoteGuestRequest(
        @NotBlank(message = "게스트 이름은 필수입니다.")
        String name,

        @NotNull(message = "게스트 성별은 필수입니다.")
        Gender gender,

        @NotBlank(message = "게스트 레벨은 필수입니다.")
        String level
) {
}
