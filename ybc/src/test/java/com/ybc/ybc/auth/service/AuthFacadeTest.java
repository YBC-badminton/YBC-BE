package com.ybc.ybc.auth.service;

import com.ybc.ybc.auth.dto.AuthTokenResponse;
import com.ybc.ybc.auth.dto.KakaoLoginResponse;
import com.ybc.ybc.auth.dto.KakaoLoginResult;
import com.ybc.ybc.auth.dto.MemberSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthFacadeTest {
    @Mock
    private KakaoAuthService kakaoAuthService;

    @Mock
    private JwtTokenService jwtTokenService;

    @InjectMocks
    private AuthFacade authFacade;

    @Test
    void loginWithKakaoReturnsLoginResponseWithIssuedTokens() {
        MemberSummary memberSummary = new MemberSummary(
                1L,
                "KAKAO",
                "123456789",
                "cabbage@example.com",
                "양배추"
        );
        KakaoLoginResult loginResult = new KakaoLoginResult(true, memberSummary);
        AuthTokenResponse authTokenResponse = AuthTokenResponse.of(
                "access-token",
                3600L,
                "refresh-token",
                1209600L
        );

        when(kakaoAuthService.loginWithAuthorizationCode("auth-code")).thenReturn(loginResult);
        when(jwtTokenService.issueTokens(memberSummary)).thenReturn(authTokenResponse);

        KakaoLoginResponse response = authFacade.loginWithKakao("auth-code");

        assertThat(response.firstLogin()).isTrue();
        assertThat(response.member().id()).isEqualTo(1L);
        assertThat(response.member().provider()).isEqualTo("KAKAO");
        assertThat(response.tokens()).isEqualTo(authTokenResponse);
    }
}
