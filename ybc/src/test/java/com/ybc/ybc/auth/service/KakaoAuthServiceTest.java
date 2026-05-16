package com.ybc.ybc.auth.service;

import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.domain.SocialProvider;
import com.ybc.ybc.auth.dto.KakaoLoginResult;
import com.ybc.ybc.auth.dto.KakaoUserProfile;
import com.ybc.ybc.auth.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KakaoAuthServiceTest {
    @Mock
    private KakaoApiClient kakaoApiClient;

    @Mock
    private KakaoLoginUrlProvider kakaoLoginUrlProvider;

    @Mock
    private KakaoFrontendRedirectService kakaoFrontendRedirectService;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private KakaoAuthService kakaoAuthService;

    @Test
    void loginWithAuthorizationCodeCreatesNewMemberOnFirstLogin() {
        KakaoUserProfile kakaoUserProfile = new KakaoUserProfile(
                "123456789",
                "양배추",
                "ybc@example.com"
        );

        when(kakaoApiClient.fetchUserProfile("auth-code")).thenReturn(kakaoUserProfile);
        when(memberRepository.findBySocialProviderAndSocialId(SocialProvider.KAKAO, "123456789"))
                .thenReturn(Optional.empty());
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> {
            Member member = invocation.getArgument(0);
            ReflectionTestUtils.setField(member, "id", 1L);
            return member;
        });

        KakaoLoginResult result = kakaoAuthService.loginWithAuthorizationCode("auth-code");

        assertThat(result.firstLogin()).isTrue();
        assertThat(result.member().id()).isEqualTo(1L);
        assertThat(result.member().provider()).isEqualTo("KAKAO");
        assertThat(result.member().nickname()).isEqualTo("양배추");
    }

    @Test
    void loginWithAuthorizationCodeUpdatesExistingMemberProfile() {
        Member existingMember = Member.createSocialMember(
                SocialProvider.KAKAO,
                "123456789",
                "기존닉네임",
                "old@example.com"
        );
        ReflectionTestUtils.setField(existingMember, "id", 7L);

        KakaoUserProfile kakaoUserProfile = new KakaoUserProfile(
                "123456789",
                "새닉네임",
                "new@example.com"
        );

        when(kakaoApiClient.fetchUserProfile("auth-code")).thenReturn(kakaoUserProfile);
        when(memberRepository.findBySocialProviderAndSocialId(SocialProvider.KAKAO, "123456789"))
                .thenReturn(Optional.of(existingMember));
        when(memberRepository.save(existingMember)).thenReturn(existingMember);

        KakaoLoginResult result = kakaoAuthService.loginWithAuthorizationCode("auth-code");

        assertThat(result.firstLogin()).isFalse();
        assertThat(existingMember.getNickname()).isEqualTo("새닉네임");
        assertThat(existingMember.getEmail()).isEqualTo("new@example.com");
        assertThat(result.member().id()).isEqualTo(7L);
    }

    @Test
    void getKakaoLoginUrlIncludesResolvedState() {
        when(kakaoFrontendRedirectService.createState("http://localhost:3000/auth/kakao/callback"))
                .thenReturn("encoded-state");
        when(kakaoLoginUrlProvider.createLoginUrl("encoded-state"))
                .thenReturn("https://kauth.kakao.com/oauth/authorize?state=encoded-state");

        String loginUrl = kakaoAuthService.getKakaoLoginUrl("http://localhost:3000/auth/kakao/callback");

        assertThat(loginUrl).isEqualTo("https://kauth.kakao.com/oauth/authorize?state=encoded-state");
    }
}
