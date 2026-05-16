package com.ybc.ybc.auth.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "app.auth.kakao")
public record KakaoOAuthProperties(
        @NotBlank String clientId,
        String clientSecret,
        @NotBlank String redirectUri,
        @NotBlank String frontendRedirectUri,
        @NotEmpty List<@NotBlank String> allowedFrontendRedirectUris,
        @NotBlank String authorizationBaseUrl,
        @NotBlank String tokenUrl,
        @NotBlank String userInfoUrl,
        String scope
) {
}
