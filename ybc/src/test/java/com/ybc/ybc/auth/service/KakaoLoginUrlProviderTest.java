package com.ybc.ybc.auth.service;

import com.ybc.ybc.auth.config.KakaoOAuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KakaoLoginUrlProviderTest {

    @Test
    void createLoginUrlBuildsExpectedQueryString() {
        String state = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("http://localhost:3000/auth/kakao/callback".getBytes(StandardCharsets.UTF_8));
        KakaoOAuthProperties properties = new KakaoOAuthProperties(
                "test-client-id",
                "",
                "http://localhost:8080/api/v1/auth/kakao/callback",
                "https://ybc-fe.vercel.app/auth/kakao/callback",
                List.of(
                        "https://ybc-fe.vercel.app/auth/kakao/callback",
                        "http://localhost:3000/auth/kakao/callback"
                ),
                "https://kauth.kakao.com/oauth/authorize",
                "https://kauth.kakao.com/oauth/token",
                "https://kapi.kakao.com/v2/user/me",
                "profile_nickname,account_email"
        );

        KakaoLoginUrlProvider provider = new KakaoLoginUrlProvider(properties);

        String loginUrl = provider.createLoginUrl(state);

        assertThat(loginUrl).contains("client_id=test-client-id");
        assertThat(loginUrl).contains("response_type=code");
        assertThat(loginUrl).contains("redirect_uri=http://localhost:8080/api/v1/auth/kakao/callback");
        assertThat(loginUrl).contains("scope=profile_nickname,account_email");
        assertThat(UriComponentsBuilder.fromUriString(loginUrl).build().getQueryParams().getFirst("state"))
                .isEqualTo(state);
    }
}
