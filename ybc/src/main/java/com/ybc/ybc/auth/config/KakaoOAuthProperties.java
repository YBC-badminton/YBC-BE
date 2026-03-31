package com.ybc.ybc.auth.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.auth.kakao")
public record KakaoOAuthProperties(
        @NotBlank String clientId,
        String clientSecret,
        @NotBlank String redirectUri,
        @NotBlank String authorizationBaseUrl,
        @NotBlank String tokenUrl,
        @NotBlank String userInfoUrl,
        String scope
) {
}
