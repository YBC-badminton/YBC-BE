package com.ybc.ybc.auth.dto;

public record KakaoLoginUrlResponse(
        String provider,
        String loginUrl
) {
}
