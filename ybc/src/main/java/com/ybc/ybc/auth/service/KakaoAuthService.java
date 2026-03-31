package com.ybc.ybc.auth.service;

import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.domain.SocialProvider;
import com.ybc.ybc.auth.dto.KakaoLoginResult;
import com.ybc.ybc.auth.dto.KakaoUserProfile;
import com.ybc.ybc.auth.dto.MemberSummary;
import com.ybc.ybc.auth.repository.MemberRepository;
import com.ybc.ybc.global.exception.ExceptionCode;
import com.ybc.ybc.global.exception.YbcException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KakaoAuthService {
    private final KakaoApiClient kakaoApiClient;
    private final KakaoLoginUrlProvider kakaoLoginUrlProvider;
    private final MemberRepository memberRepository;

    // 카카오 로그인 인가 URL 생성
    public String getKakaoLoginUrl() {
        return kakaoLoginUrlProvider.createLoginUrl();
    }

    // 인가 코드로 카카오 회원을 조회하고 우리 서비스 회원으로 저장
    @Transactional
    public KakaoLoginResult loginWithAuthorizationCode(String authorizationCode) {
        KakaoUserProfile kakaoUserProfile = kakaoApiClient.fetchUserProfile(authorizationCode);
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
        return new KakaoLoginResult(firstLogin, MemberSummary.from(savedMember));
    }

    // 회원 ID로 현재 로그인한 회원 정보 조회
    public MemberSummary getCurrentMember(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new YbcException(ExceptionCode.UNAUTHORIZED_USER));
        return MemberSummary.from(member);
    }
}
