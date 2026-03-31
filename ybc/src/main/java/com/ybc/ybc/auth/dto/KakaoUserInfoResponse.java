package com.ybc.ybc.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoUserInfoResponse(
        Long id,
        Properties properties,
        @JsonProperty("kakao_account") KakaoAccount kakaoAccount
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Properties(
            String nickname
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoAccount(
            String email
    ) {
    }
}
