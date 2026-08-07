package com.ybc.ybc.auth.service;

import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.domain.SocialProvider;
import com.ybc.ybc.auth.dto.KakaoLoginResult;
import com.ybc.ybc.auth.dto.KakaoUserProfile;
import com.ybc.ybc.auth.dto.MemberSummary;
import com.ybc.ybc.auth.repository.MemberRepository;
import com.ybc.ybc.global.exception.ExceptionCode;
import com.ybc.ybc.global.exception.YbcException;
import com.ybc.ybc.members.domain.ClubMember;
import com.ybc.ybc.members.repository.ClubMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KakaoAuthService {
    private final KakaoApiClient kakaoApiClient;
    private final KakaoLoginUrlProvider kakaoLoginUrlProvider;
    private final KakaoFrontendRedirectService kakaoFrontendRedirectService;
    private final MemberRepository memberRepository;
    private final ClubMemberRepository clubMemberRepository;

    // 카카오 로그인 인가 URL 생성
    public String getKakaoLoginUrl(String frontendRedirectUri) {
        String state = kakaoFrontendRedirectService.createState(frontendRedirectUri);
        return kakaoLoginUrlProvider.createLoginUrl(state);
    }

    public String resolveFrontendRedirectUri(String state) {
        return kakaoFrontendRedirectService.resolveFromState(state);
    }

    public String getDefaultFrontendRedirectUri() {
        return kakaoFrontendRedirectService.resolveFromState(null);
    }

    // 인가 코드로 카카오 회원을 조회하고 우리 서비스 회원으로 저장
    @Transactional
    public KakaoLoginResult loginWithAuthorizationCode(String authorizationCode) {
        KakaoUserProfile kakaoUserProfile = kakaoApiClient.fetchUserProfile(authorizationCode);
        String normalizedEmail = normalizeEmail(kakaoUserProfile.email());
        if (normalizedEmail == null) {
            throw new YbcException(ExceptionCode.KAKAO_EMAIL_REQUIRED);
        }

        ClubMember clubMember = clubMemberRepository.findFirstByNormalizedEmail(normalizedEmail)
                .orElse(null);
        Member member = memberRepository.findBySocialProviderAndSocialId(SocialProvider.KAKAO, kakaoUserProfile.socialId())
                .orElse(null);
        boolean firstLogin = member == null;

        if (member == null) {
            member = Member.createSocialMember(
                    SocialProvider.KAKAO,
                    kakaoUserProfile.socialId(),
                    kakaoUserProfile.nickname(),
                    kakaoUserProfile.email()
            );
        }

        member.updateProfile(
                kakaoUserProfile.nickname(),
                kakaoUserProfile.email()
        );

        Member savedMember = memberRepository.save(member);
        if (clubMember != null) {
            clubMember.linkMember(savedMember.getId());
        }
        return new KakaoLoginResult(firstLogin, MemberSummary.from(savedMember, clubMember));
    }

    // 회원 ID로 현재 로그인한 회원 정보 조회
    public MemberSummary getCurrentMember(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new YbcException(ExceptionCode.UNAUTHORIZED_USER));
        ClubMember clubMember = clubMemberRepository.findByMemberId(memberId)
                .orElse(null);
        return MemberSummary.from(member, clubMember);
    }

    private String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
