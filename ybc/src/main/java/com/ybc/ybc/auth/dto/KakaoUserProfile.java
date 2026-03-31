package com.ybc.ybc.auth.dto;

public record KakaoUserProfile(
        String socialId,
        String nickname,
        String email
) {
}
