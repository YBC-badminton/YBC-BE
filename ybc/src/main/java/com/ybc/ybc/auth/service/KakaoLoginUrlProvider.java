package com.ybc.ybc.auth.service;

import com.ybc.ybc.auth.config.KakaoOAuthProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
public class KakaoLoginUrlProvider {
    private final KakaoOAuthProperties kakaoOAuthProperties;

    // 설정값을 바탕으로 카카오 로그인 인가 URL 만듦
    public String createLoginUrl(String state) {
        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder
                .fromUriString(kakaoOAuthProperties.authorizationBaseUrl())
                .queryParam("client_id", kakaoOAuthProperties.clientId())
                .queryParam("redirect_uri", kakaoOAuthProperties.redirectUri())
                .queryParam("response_type", "code");

        if (StringUtils.hasText(state)) {
            uriComponentsBuilder.queryParam("state", state);
        }

        if (StringUtils.hasText(kakaoOAuthProperties.scope())) {
            uriComponentsBuilder.queryParam("scope", kakaoOAuthProperties.scope());
        }

        return uriComponentsBuilder.build().encode().toUriString();
    }
}
