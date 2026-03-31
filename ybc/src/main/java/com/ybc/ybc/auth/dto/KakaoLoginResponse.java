package com.ybc.ybc.auth.dto;

public record KakaoLoginResponse(
        boolean firstLogin,
        AuthTokenResponse tokens,
        MemberResponse member
) {
    public static KakaoLoginResponse from(KakaoLoginResult loginResult, AuthTokenResponse authTokenResponse) {
        return new KakaoLoginResponse(
                loginResult.firstLogin(),
                authTokenResponse,
                MemberResponse.from(loginResult.member())
        );
    }
}
