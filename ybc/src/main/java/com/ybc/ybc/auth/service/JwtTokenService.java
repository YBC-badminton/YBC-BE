package com.ybc.ybc.auth.service;

import com.ybc.ybc.auth.config.JwtProperties;
import com.ybc.ybc.auth.domain.AuthTokenType;
import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.dto.AuthTokenResponse;
import com.ybc.ybc.auth.dto.MemberSummary;
import com.ybc.ybc.auth.repository.MemberRepository;
import com.ybc.ybc.global.exception.ExceptionCode;
import com.ybc.ybc.global.exception.YbcException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JwtTokenService {
    private static final String TOKEN_TYPE_CLAIM = "tokenType";

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final JwtProperties jwtProperties;
    private final MemberRepository memberRepository;
    private final RefreshTokenService refreshTokenService;

    // 회원 정보로 액세스 토큰과 리프레시 토큰 발급
    @Transactional
    public AuthTokenResponse issueTokens(MemberSummary memberSummary) {
        String accessToken = createToken(memberSummary, AuthTokenType.ACCESS);
        String refreshToken = createToken(memberSummary, AuthTokenType.REFRESH);
        refreshTokenService.save(memberSummary.id(), refreshToken);

        return AuthTokenResponse.of(
                accessToken,
                jwtProperties.accessTokenExpirationSeconds(),
                refreshToken,
                jwtProperties.refreshTokenExpirationSeconds()
        );
    }

    // 리프레시 토큰을 검증한 뒤 새로운 토큰 쌍 재발급
    @Transactional
    public AuthTokenResponse reissueTokens(String refreshToken) {
        Jwt jwt = decode(refreshToken);
        validateTokenType(jwt, AuthTokenType.REFRESH);

        Long memberId = Long.parseLong(jwt.getSubject());
        refreshTokenService.validate(memberId, refreshToken);

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new YbcException(ExceptionCode.UNAUTHORIZED_USER));

        return issueTokens(MemberSummary.from(member));
    }

    // 회원의 저장된 리프레시 토큰 삭제
    @Transactional
    public void logout(Long memberId) {
        refreshTokenService.delete(memberId);
    }

    // 토큰 종류에 맞는 JWT 본문을 만들어 서명된 토큰 문자열로 변환
    private String createToken(MemberSummary memberSummary, AuthTokenType authTokenType) {
        Instant now = Instant.now();
        long expirationSeconds = authTokenType == AuthTokenType.ACCESS
                ? jwtProperties.accessTokenExpirationSeconds()
                : jwtProperties.refreshTokenExpirationSeconds();

        JwtClaimsSet claimsSet = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(expirationSeconds))
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(memberSummary.id()))
                .claim(TOKEN_TYPE_CLAIM, authTokenType.value())
                .claim("provider", memberSummary.provider())
                .claim("socialId", memberSummary.socialId())
                .claim("email", memberSummary.email())
                .claim("nickname", memberSummary.nickname())
                .claim("isAdmin", memberSummary.isAdmin())
                .build();

        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claimsSet)).getTokenValue();
    }

    // 전달된 JWT 문자열을 해석하고 유효성 검증
    private Jwt decode(String token) {
        if (!StringUtils.hasText(token)) {
            throw new YbcException(ExceptionCode.INVALID_JWT_TOKEN);
        }

        try {
            return jwtDecoder.decode(token);
        } catch (JwtException exception) {
            throw new YbcException(ExceptionCode.INVALID_JWT_TOKEN);
        }
    }

    // JWT가 기대한 토큰 타입인지 확인
    private void validateTokenType(Jwt jwt, AuthTokenType expectedType) {
        if (!expectedType.value().equals(jwt.getClaimAsString(TOKEN_TYPE_CLAIM))) {
            throw new YbcException(ExceptionCode.INVALID_JWT_TOKEN);
        }
    }
}
