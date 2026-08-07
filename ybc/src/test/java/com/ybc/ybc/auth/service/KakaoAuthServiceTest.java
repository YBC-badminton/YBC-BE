package com.ybc.ybc.auth.service;

import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.domain.SocialProvider;
import com.ybc.ybc.auth.dto.KakaoLoginResult;
import com.ybc.ybc.auth.dto.KakaoUserProfile;
import com.ybc.ybc.auth.dto.MemberSummary;
import com.ybc.ybc.auth.repository.MemberRepository;
import com.ybc.ybc.global.exception.YbcException;
import com.ybc.ybc.members.domain.ClubMember;
import com.ybc.ybc.members.repository.ClubMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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

    @Mock
    private ClubMemberRepository clubMemberRepository;

    @InjectMocks
    private KakaoAuthService kakaoAuthService;

    @Test
    void loginWithAuthorizationCodeCreatesNewMemberOnFirstLogin() {
        ClubMember clubMember = ClubMember.create(
                "양배추",
                "이화여자대학교",
                "010-1234-5678",
                "03",
                "10기",
                "ybc@example.com",
                null
        );
        KakaoUserProfile kakaoUserProfile = new KakaoUserProfile(
                "123456789",
                "양배추",
                "YBC@example.com"
        );

        when(kakaoApiClient.fetchUserProfile("auth-code")).thenReturn(kakaoUserProfile);
        when(clubMemberRepository.findFirstByNormalizedEmail("ybc@example.com"))
                .thenReturn(Optional.of(clubMember));
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
        assertThat(result.member().term()).isEqualTo("10기");
        assertThat(result.member().isAdmin()).isFalse();
        assertThat(clubMember.getMemberId()).isEqualTo(1L);
    }

    @Test
    void loginWithAuthorizationCodeUpdatesExistingMemberProfile() {
        ClubMember clubMember = ClubMember.create(
                "운영진닉네임",
                "이화여자대학교",
                "010-1234-5678",
                "03",
                "10기",
                "new@example.com",
                null
        );
        Member existingMember = Member.createSocialMember(
                SocialProvider.KAKAO,
                "123456789",
                "기존닉네임",
                "old@example.com",
                true
        );
        ReflectionTestUtils.setField(existingMember, "id", 7L);

        KakaoUserProfile kakaoUserProfile = new KakaoUserProfile(
                "123456789",
                "새닉네임",
                "new@example.com"
        );

        when(kakaoApiClient.fetchUserProfile("auth-code")).thenReturn(kakaoUserProfile);
        when(clubMemberRepository.findFirstByNormalizedEmail("new@example.com"))
                .thenReturn(Optional.of(clubMember));
        when(memberRepository.findBySocialProviderAndSocialId(SocialProvider.KAKAO, "123456789"))
                .thenReturn(Optional.of(existingMember));
        when(memberRepository.save(existingMember)).thenReturn(existingMember);

        KakaoLoginResult result = kakaoAuthService.loginWithAuthorizationCode("auth-code");

        assertThat(result.firstLogin()).isFalse();
        assertThat(existingMember.getNickname()).isEqualTo("새닉네임");
        assertThat(existingMember.getEmail()).isEqualTo("new@example.com");
        assertThat(result.member().id()).isEqualTo(7L);
        assertThat(result.member().term()).isEqualTo("10기");
        assertThat(result.member().isAdmin()).isTrue();
        assertThat(clubMember.getMemberId()).isEqualTo(7L);
    }

    @Test
    void getCurrentMemberReturnsClubMemberTermAndAdminFlag() {
        Member member = Member.createSocialMember(
                SocialProvider.KAKAO,
                "123456789",
                "양배추",
                "ybc@example.com",
                true
        );
        ReflectionTestUtils.setField(member, "id", 1L);

        ClubMember clubMember = ClubMember.create(
                "양배추",
                "이화여자대학교",
                "010-1234-5678",
                "03",
                "10기",
                "ybc@example.com",
                null
        );

        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(clubMemberRepository.findByMemberId(1L)).thenReturn(Optional.of(clubMember));

        MemberSummary result = kakaoAuthService.getCurrentMember(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.term()).isEqualTo("10기");
        assertThat(result.isAdmin()).isTrue();
        verify(clubMemberRepository).findByMemberId(1L);
    }

    @Test
    void loginWithAuthorizationCodeThrowsWhenKakaoEmailIsMissing() {
        when(kakaoApiClient.fetchUserProfile("auth-code"))
                .thenReturn(new KakaoUserProfile("123456789", "양배추", null));

        assertThatThrownBy(() -> kakaoAuthService.loginWithAuthorizationCode("auth-code"))
                .isInstanceOf(YbcException.class)
                .hasMessage("카카오 계정 이메일 정보가 필요합니다.");
    }

    @Test
    void loginWithAuthorizationCodeAllowsUserWhenClubMemberEmailDoesNotExist() {
        when(kakaoApiClient.fetchUserProfile("auth-code"))
                .thenReturn(new KakaoUserProfile("123456789", "양배추", "outsider@example.com"));
        when(clubMemberRepository.findFirstByNormalizedEmail("outsider@example.com"))
                .thenReturn(Optional.empty());
        when(memberRepository.findBySocialProviderAndSocialId(SocialProvider.KAKAO, "123456789"))
                .thenReturn(Optional.empty());
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> {
            Member member = invocation.getArgument(0);
            ReflectionTestUtils.setField(member, "id", 3L);
            return member;
        });

        KakaoLoginResult result = kakaoAuthService.loginWithAuthorizationCode("auth-code");

        assertThat(result.firstLogin()).isTrue();
        assertThat(result.member().id()).isEqualTo(3L);
        assertThat(result.member().email()).isEqualTo("outsider@example.com");
        assertThat(result.member().term()).isNull();
        assertThat(result.member().isAdmin()).isFalse();
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
