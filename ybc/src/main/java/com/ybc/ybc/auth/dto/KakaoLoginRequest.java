package com.ybc.ybc.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record KakaoLoginRequest(
        @NotBlank(message = "인가 코드가 필요합니다.") String code
) {
}
