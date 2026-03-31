package com.ybc.ybc.auth.service;

import com.ybc.ybc.auth.config.KakaoOAuthProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KakaoLoginUrlProviderTest {

    @Test
    void createLoginUrlBuildsExpectedQueryString() {
        KakaoOAuthProperties properties = new KakaoOAuthProperties(
                "test-client-id",
                "",
                "http://localhost:8080/api/v1/auth/kakao/callback",
                "https://kauth.kakao.com/oauth/authorize",
                "https://kauth.kakao.com/oauth/token",
                "https://kapi.kakao.com/v2/user/me",
                "profile_nickname,account_email"
        );

        KakaoLoginUrlProvider provider = new KakaoLoginUrlProvider(properties);

        String loginUrl = provider.createLoginUrl();

        assertThat(loginUrl).contains("client_id=test-client-id");
        assertThat(loginUrl).contains("response_type=code");
        assertThat(loginUrl).contains("redirect_uri=http://localhost:8080/api/v1/auth/kakao/callback");
        assertThat(loginUrl).contains("scope=profile_nickname,account_email");
    }
}
