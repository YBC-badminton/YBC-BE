package com.ybc.ybc.auth.service;

import com.ybc.ybc.auth.dto.AuthTokenResponse;
import com.ybc.ybc.auth.dto.CurrentMemberResponse;
import com.ybc.ybc.auth.dto.KakaoLoginResponse;
import com.ybc.ybc.auth.dto.KakaoLoginResult;
import com.ybc.ybc.auth.dto.MemberSummary;
import com.ybc.ybc.vote.service.MemberActiveVoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthFacade {
    private final KakaoAuthService kakaoAuthService;
    private final JwtTokenService jwtTokenService;
    private final MemberActiveVoteService memberActiveVoteService;

    @Transactional
    public KakaoLoginResponse loginWithKakao(String authorizationCode) {
        KakaoLoginResult loginResult = kakaoAuthService.loginWithAuthorizationCode(authorizationCode);
        AuthTokenResponse authTokenResponse = jwtTokenService.issueTokens(loginResult.member());
        return KakaoLoginResponse.from(loginResult, authTokenResponse);
    }

    public CurrentMemberResponse getCurrentMember(Long memberId) {
        MemberSummary memberSummary = kakaoAuthService.getCurrentMember(memberId);
        return CurrentMemberResponse.from(
                memberSummary,
                memberActiveVoteService.getParticipatedActiveVotes(memberId)
        );
    }
}
