package com.ybc.ybc.auth.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.auth.jwt")
public record JwtProperties(
        @NotBlank String secretKey,
        @NotBlank String issuer,
        @Min(1) long accessTokenExpirationSeconds,
        @Min(1) long refreshTokenExpirationSeconds
) {
}
