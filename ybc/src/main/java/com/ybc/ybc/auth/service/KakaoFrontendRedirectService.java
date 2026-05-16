package com.ybc.ybc.auth.service;

import com.ybc.ybc.auth.config.KakaoOAuthProperties;
import com.ybc.ybc.global.exception.ExceptionCode;
import com.ybc.ybc.global.exception.YbcException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class KakaoFrontendRedirectService {
    private final KakaoOAuthProperties kakaoOAuthProperties;

    public String createState(String requestedFrontendRedirectUri) {
        String resolvedFrontendRedirectUri = resolveFrontendRedirectUri(requestedFrontendRedirectUri);
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(resolvedFrontendRedirectUri.getBytes(StandardCharsets.UTF_8));
    }

    public String resolveFromState(String state) {
        if (!StringUtils.hasText(state)) {
            return resolveFrontendRedirectUri(null);
        }

        try {
            String decodedState = new String(Base64.getUrlDecoder().decode(state), StandardCharsets.UTF_8);
            return resolveFrontendRedirectUri(decodedState);
        } catch (IllegalArgumentException exception) {
            throw new YbcException(ExceptionCode.INVALID_FRONTEND_REDIRECT_URI);
        }
    }

    private String resolveFrontendRedirectUri(String frontendRedirectUri) {
        String candidate = StringUtils.hasText(frontendRedirectUri)
                ? frontendRedirectUri.trim()
                : kakaoOAuthProperties.frontendRedirectUri();
        String normalizedCandidate = normalize(candidate);
        if (!allowedFrontendRedirectUris().contains(normalizedCandidate)) {
            throw new YbcException(ExceptionCode.INVALID_FRONTEND_REDIRECT_URI);
        }
        return normalizedCandidate;
    }

    private Set<String> allowedFrontendRedirectUris() {
        return Stream.concat(
                        Stream.of(kakaoOAuthProperties.frontendRedirectUri()),
                        kakaoOAuthProperties.allowedFrontendRedirectUris().stream()
                )
                .filter(StringUtils::hasText)
                .map(this::normalize)
                .collect(Collectors.toUnmodifiableSet());
    }

    private String normalize(String frontendRedirectUri) {
        try {
            URI uri = URI.create(frontendRedirectUri.trim());
            validate(uri);
            return new URI(
                    uri.getScheme().toLowerCase(Locale.ROOT),
                    null,
                    uri.getHost().toLowerCase(Locale.ROOT),
                    uri.getPort(),
                    normalizePath(uri.getPath()),
                    null,
                    null
            ).toString();
        } catch (IllegalArgumentException | URISyntaxException exception) {
            throw new YbcException(ExceptionCode.INVALID_FRONTEND_REDIRECT_URI);
        }
    }

    private void validate(URI uri) {
        String scheme = uri.getScheme();
        if (!uri.isAbsolute()
                || !StringUtils.hasText(scheme)
                || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || !StringUtils.hasText(uri.getHost())
                || !StringUtils.hasText(uri.getPath())
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new YbcException(ExceptionCode.INVALID_FRONTEND_REDIRECT_URI);
        }
    }

    private String normalizePath(String path) {
        if (path.length() > 1 && path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }
}
