package com.ybc.ybc.auth.service;

import com.ybc.ybc.global.exception.ExceptionCode;
import com.ybc.ybc.global.exception.YbcException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class SwaggerLoginService {
    public static final String SWAGGER_ACCESS_TOKEN_COOKIE_NAME = "ybc_swagger_access_token";

    private final JwtDecoder jwtDecoder;
    private final Converter<Jwt, UsernamePasswordAuthenticationToken> jwtAuthenticationConverter;
    private final SwaggerAccessService swaggerAccessService;

    public ResponseCookie createSwaggerAccessCookie(String accessToken, boolean secure) {
        String normalizedAccessToken = normalizeAccessToken(accessToken);
        Jwt jwt = decode(normalizedAccessToken);
        Authentication authentication = createAuthentication(jwt);
        if (!swaggerAccessService.canAccess(authentication)) {
            throw new YbcException(ExceptionCode.UNAUTHORIZED_USER);
        }
        return ResponseCookie.from(SWAGGER_ACCESS_TOKEN_COOKIE_NAME, normalizedAccessToken)
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .sameSite("Lax")
                .maxAge(resolveCookieMaxAge(jwt))
                .build();
    }

    public ResponseCookie clearSwaggerAccessCookie(boolean secure) {
        return ResponseCookie.from(SWAGGER_ACCESS_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .sameSite("Lax")
                .maxAge(Duration.ZERO)
                .build();
    }

    private String normalizeAccessToken(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            throw new YbcException(ExceptionCode.INVALID_JWT_TOKEN);
        }

        String trimmedAccessToken = accessToken.trim();
        if (trimmedAccessToken.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String bearerToken = trimmedAccessToken.substring(7).trim();
            if (!StringUtils.hasText(bearerToken)) {
                throw new YbcException(ExceptionCode.INVALID_JWT_TOKEN);
            }
            return bearerToken;
        }
        return trimmedAccessToken;
    }

    private Jwt decode(String accessToken) {
        try {
            return jwtDecoder.decode(accessToken);
        } catch (JwtException exception) {
            throw new YbcException(ExceptionCode.INVALID_JWT_TOKEN, exception);
        }
    }

    private Authentication createAuthentication(Jwt jwt) {
        try {
            UsernamePasswordAuthenticationToken authentication = jwtAuthenticationConverter.convert(jwt);
            if (authentication == null) {
                throw new YbcException(ExceptionCode.INVALID_JWT_TOKEN);
            }
            return authentication;
        } catch (BadCredentialsException | IllegalArgumentException exception) {
            throw new YbcException(ExceptionCode.INVALID_JWT_TOKEN, exception);
        }
    }

    private Duration resolveCookieMaxAge(Jwt jwt) {
        Instant expiresAt = jwt.getExpiresAt();
        if (expiresAt == null) {
            throw new YbcException(ExceptionCode.INVALID_JWT_TOKEN);
        }

        long remainingSeconds = Duration.between(Instant.now(), expiresAt).getSeconds();
        if (remainingSeconds <= 0) {
            throw new YbcException(ExceptionCode.INVALID_JWT_TOKEN);
        }
        return Duration.ofSeconds(remainingSeconds);
    }
}
