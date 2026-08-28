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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Service
@Slf4j
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
        String maskedSocialId = maskSocialId(kakaoUserProfile.socialId());
        String maskedEmail = maskEmail(normalizedEmail);

        if (normalizedEmail == null) {
            log.warn("Kakao login rejected because email is missing. socialId={}", maskedSocialId);
            throw new YbcException(ExceptionCode.KAKAO_EMAIL_REQUIRED);
        }

        ClubMember clubMember = clubMemberRepository.findFirstByNormalizedEmail(normalizedEmail)
                .orElse(null);
        if (clubMember == null) {
            log.warn("Kakao login rejected because no club member matched email. socialId={}, email={}",
                    maskedSocialId, maskedEmail);
            throw new YbcException(ExceptionCode.CLUB_MEMBER_LOGIN_FORBIDDEN);
        }

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
        if (clubMember.getMemberId() != null && !clubMember.getMemberId().equals(savedMember.getId())) {
            if (memberRepository.existsById(clubMember.getMemberId())) {
                log.warn("Club member is already linked to a different member id during Kakao login. clubMemberId={}, linkedMemberId={}, savedMemberId={}, socialId={}, email={}",
                        clubMember.getId(), clubMember.getMemberId(), savedMember.getId(), maskedSocialId, maskedEmail);
            } else {
                log.info("Relinking club member to recreated member account after stale link detection. clubMemberId={}, staleMemberId={}, savedMemberId={}, socialId={}, email={}",
                        clubMember.getId(), clubMember.getMemberId(), savedMember.getId(), maskedSocialId, maskedEmail);
                clubMember.relinkMember(savedMember.getId());
            }
        } else {
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

    private String maskEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return "<empty>";
        }

        int separatorIndex = email.indexOf('@');
        if (separatorIndex <= 0 || separatorIndex == email.length() - 1) {
            return maskValue(email);
        }

        String localPart = email.substring(0, separatorIndex);
        String domain = email.substring(separatorIndex + 1);
        return maskValue(localPart) + "@" + domain;
    }

    private String maskSocialId(String socialId) {
        if (!StringUtils.hasText(socialId)) {
            return "<empty>";
        }
        return maskValue(socialId);
    }

    private String maskValue(String value) {
        if (value.length() <= 2) {
            return "*".repeat(value.length());
        }
        if (value.length() <= 4) {
            return value.charAt(0) + "**" + value.charAt(value.length() - 1);
        }
        return value.substring(0, 2) + "***" + value.substring(value.length() - 2);
    }
}
