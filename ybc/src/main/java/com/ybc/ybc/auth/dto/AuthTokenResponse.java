package com.ybc.ybc.auth.dto;

public record AuthTokenResponse(
        String tokenType,
        String accessToken,
        long accessTokenExpiresIn,
        String refreshToken,
        long refreshTokenExpiresIn
) {
    public static AuthTokenResponse of(String accessToken,
                                       long accessTokenExpiresIn,
                                       String refreshToken,
                                       long refreshTokenExpiresIn) {
        return new AuthTokenResponse(
                "Bearer",
                accessToken,
                accessTokenExpiresIn,
                refreshToken,
                refreshTokenExpiresIn
        );
    }
}
