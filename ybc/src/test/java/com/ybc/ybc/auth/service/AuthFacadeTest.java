package com.ybc.ybc.auth.service;

import com.ybc.ybc.auth.dto.AuthTokenResponse;
import com.ybc.ybc.auth.dto.CurrentMemberResponse;
import com.ybc.ybc.auth.dto.KakaoLoginResponse;
import com.ybc.ybc.auth.dto.KakaoLoginResult;
import com.ybc.ybc.auth.dto.MemberSummary;
import com.ybc.ybc.vote.dto.response.ActiveVoteSummaryResponse;
import com.ybc.ybc.vote.service.MemberActiveVoteService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthFacadeTest {
    @Mock
    private KakaoAuthService kakaoAuthService;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private MemberActiveVoteService memberActiveVoteService;

    @InjectMocks
    private AuthFacade authFacade;

    @Test
    void loginWithKakaoReturnsLoginResponseWithIssuedTokens() {
        MemberSummary memberSummary = new MemberSummary(
                1L,
                "KAKAO",
                "123456789",
                "cabbage@example.com",
                "양배추",
                "10기",
                true
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
        assertThat(response.member().term()).isEqualTo("10기");
        assertThat(response.member().isAdmin()).isTrue();
        assertThat(response.tokens()).isEqualTo(authTokenResponse);
    }

    @Test
    void getCurrentMemberReturnsActiveVotesWithMemberInfo() {
        MemberSummary memberSummary = new MemberSummary(
                1L,
                "KAKAO",
                "123456789",
                "cabbage@example.com",
                "양배추",
                "10기",
                false
        );
        List<ActiveVoteSummaryResponse> activeVotes = List.of(
                new ActiveVoteSummaryResponse(10L, "정기모임 9월 1주차")
        );

        when(kakaoAuthService.getCurrentMember(1L)).thenReturn(memberSummary);
        when(memberActiveVoteService.getParticipatedActiveVotes(1L)).thenReturn(activeVotes);

        CurrentMemberResponse response = authFacade.getCurrentMember(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.nickname()).isEqualTo("양배추");
        assertThat(response.activeVotes()).containsExactlyElementsOf(activeVotes);
    }
}
