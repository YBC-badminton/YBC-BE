package com.ybc.ybc.auth.service;

import com.ybc.ybc.auth.config.KakaoOAuthProperties;
import com.ybc.ybc.auth.dto.KakaoTokenResponse;
import com.ybc.ybc.auth.dto.KakaoUserInfoResponse;
import com.ybc.ybc.auth.dto.KakaoUserProfile;
import com.ybc.ybc.global.exception.ExceptionCode;
import com.ybc.ybc.global.exception.YbcException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@Slf4j
@RequiredArgsConstructor
public class KakaoApiClient {
    private final KakaoOAuthProperties kakaoOAuthProperties;
    private final RestClient restClient = RestClient.builder().build();

    // 인가 코드를 이용해 카카오 사용자 정보 조회
    public KakaoUserProfile fetchUserProfile(String authorizationCode) {
        KakaoTokenResponse tokenResponse = exchangeAuthorizationCode(authorizationCode);
        KakaoUserInfoResponse userInfoResponse = fetchUserInfo(tokenResponse.accessToken());

        if (userInfoResponse.id() == null) {
            throw new YbcException(ExceptionCode.KAKAO_AUTH_FAILED);
        }

        String socialId = String.valueOf(userInfoResponse.id());
        String nickname = userInfoResponse.properties() != null
                ? userInfoResponse.properties().nickname()
                : null;
        String email = userInfoResponse.kakaoAccount() != null
                ? userInfoResponse.kakaoAccount().email()
                : null;

        return new KakaoUserProfile(
                socialId,
                normalizeNickname(nickname, socialId),
                normalizeBlank(email)
        );
    }

    // 카카오 토큰 API에 인가 코드를 전달해 액세스 토큰 발급
    private KakaoTokenResponse exchangeAuthorizationCode(String authorizationCode) {
        try {
            MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
            requestBody.add("grant_type", "authorization_code");
            requestBody.add("client_id", kakaoOAuthProperties.clientId());
            requestBody.add("redirect_uri", kakaoOAuthProperties.redirectUri());
            requestBody.add("code", authorizationCode);

            if (StringUtils.hasText(kakaoOAuthProperties.clientSecret())) {
                requestBody.add("client_secret", kakaoOAuthProperties.clientSecret());
            }

            KakaoTokenResponse tokenResponse = restClient.post()
                    .uri(kakaoOAuthProperties.tokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(requestBody)
                    .retrieve()
                    .body(KakaoTokenResponse.class);

            if (tokenResponse == null || !StringUtils.hasText(tokenResponse.accessToken())) {
                throw new YbcException(ExceptionCode.KAKAO_AUTH_FAILED);
            }

            return tokenResponse;
        } catch (RestClientResponseException exception) {
            log.error(
                    "Failed to exchange Kakao authorization code. status={}, body={}",
                    exception.getStatusCode(),
                    exception.getResponseBodyAsString(),
                    exception
            );
            throw new YbcException(ExceptionCode.KAKAO_AUTH_FAILED);
        } catch (RestClientException exception) {
            log.error("Failed to exchange Kakao authorization code.", exception);
            throw new YbcException(ExceptionCode.KAKAO_AUTH_FAILED);
        }
    }

    // 발급받은 액세스 토큰으로 카카오 사용자 정보 조회
    private KakaoUserInfoResponse fetchUserInfo(String accessToken) {
        try {
            KakaoUserInfoResponse userInfoResponse = restClient.get()
                    .uri(kakaoOAuthProperties.userInfoUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(KakaoUserInfoResponse.class);

            if (userInfoResponse == null) {
                throw new YbcException(ExceptionCode.KAKAO_AUTH_FAILED);
            }

            return userInfoResponse;
        } catch (RestClientResponseException exception) {
            log.error(
                    "Failed to fetch Kakao user info. status={}, body={}",
                    exception.getStatusCode(),
                    exception.getResponseBodyAsString(),
                    exception
            );
            throw new YbcException(ExceptionCode.KAKAO_AUTH_FAILED);
        } catch (RestClientException exception) {
            log.error("Failed to fetch Kakao user info.", exception);
            throw new YbcException(ExceptionCode.KAKAO_AUTH_FAILED);
        }
    }

    // 카카오 닉네임이 없을 때 사용할 기본 닉네임을 만듦
    private String normalizeNickname(String nickname, String socialId) {
        if (StringUtils.hasText(nickname)) {
            return nickname;
        }
        return "kakao_" + socialId;
    }

    private String normalizeBlank(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
