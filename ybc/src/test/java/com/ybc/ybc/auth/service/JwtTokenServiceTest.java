package com.ybc.ybc.auth.service;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.ybc.ybc.auth.config.JwtProperties;
import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.domain.SocialProvider;
import com.ybc.ybc.auth.dto.AuthTokenResponse;
import com.ybc.ybc.auth.dto.MemberSummary;
import com.ybc.ybc.auth.repository.MemberRepository;
import com.ybc.ybc.global.exception.YbcException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtTokenServiceTest {
    @Mock
    private MemberRepository memberRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    private JwtTokenService jwtTokenService;
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties(
                "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
                "ybc-test",
                3600,
                1209600
        );

        SecretKey secretKey = new SecretKeySpec(
                Base64.getDecoder().decode(jwtProperties.secretKey()),
                "HmacSHA256"
        );
        JwtEncoder jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
        jwtDecoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        jwtTokenService = new JwtTokenService(
                jwtEncoder,
                jwtDecoder,
                jwtProperties,
                memberRepository,
                refreshTokenService
        );
    }

    @Test
    void issueTokensCreatesBearerTokensAndStoresRefreshToken() {
        MemberSummary memberSummary = new MemberSummary(
                1L,
                "KAKAO",
                "123456789",
                "ybc@example.com",
                "양배추",
                "10기",
                false
        );

        AuthTokenResponse tokenResponse = jwtTokenService.issueTokens(memberSummary);
        Jwt accessToken = jwtDecoder.decode(tokenResponse.accessToken());
        Jwt refreshToken = jwtDecoder.decode(tokenResponse.refreshToken());

        assertThat(tokenResponse.tokenType()).isEqualTo("Bearer");
        assertThat(tokenResponse.accessToken()).isNotBlank();
        assertThat(tokenResponse.refreshToken()).isNotBlank();
        assertThat(tokenResponse.accessTokenExpiresIn()).isEqualTo(3600);
        assertThat(tokenResponse.refreshTokenExpiresIn()).isEqualTo(1209600);
        assertThat(accessToken.getSubject()).isEqualTo("1");
        assertThat(accessToken.getClaimAsString("tokenType")).isEqualTo("access");
        assertThat(accessToken.getClaimAsBoolean("isAdmin")).isFalse();
        assertThat(refreshToken.getClaimAsString("tokenType")).isEqualTo("refresh");
        assertThat(refreshToken.getClaimAsBoolean("isAdmin")).isFalse();

        verify(refreshTokenService).save(1L, tokenResponse.refreshToken());
    }

    @Test
    void reissueTokensRotatesTokensWhenRefreshTokenIsValid() {
        Member member = Member.createSocialMember(
                SocialProvider.KAKAO,
                "123456789",
                "양배추",
                "ybc@example.com"
        );
        ReflectionTestUtils.setField(member, "id", 1L);

        AuthTokenResponse issuedTokens = jwtTokenService.issueTokens(MemberSummary.from(member));
        clearInvocations(refreshTokenService);

        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        AuthTokenResponse reissuedTokens = jwtTokenService.reissueTokens(issuedTokens.refreshToken());
        Jwt reissuedAccessToken = jwtDecoder.decode(reissuedTokens.accessToken());

        assertThat(reissuedTokens.accessToken()).isNotEqualTo(issuedTokens.accessToken());
        assertThat(reissuedTokens.refreshToken()).isNotEqualTo(issuedTokens.refreshToken());
        assertThat(reissuedAccessToken.getSubject()).isEqualTo("1");
        assertThat(reissuedAccessToken.getClaimAsString("tokenType")).isEqualTo("access");
        assertThat(reissuedAccessToken.getClaimAsBoolean("isAdmin")).isFalse();

        verify(refreshTokenService).validate(1L, issuedTokens.refreshToken());
        verify(refreshTokenService).save(1L, reissuedTokens.refreshToken());
    }

    @Test
    void issueTokensIncludesAdminClaimForAdminMember() {
        MemberSummary memberSummary = new MemberSummary(
                1L,
                "KAKAO",
                "123456789",
                "admin@example.com",
                "운영진",
                "10기",
                true
        );

        AuthTokenResponse tokenResponse = jwtTokenService.issueTokens(memberSummary);
        Jwt accessToken = jwtDecoder.decode(tokenResponse.accessToken());

        assertThat(accessToken.getClaimAsBoolean("isAdmin")).isTrue();
    }

    @Test
    void reissueTokensRejectsAccessToken() {
        MemberSummary memberSummary = new MemberSummary(
                1L,
                "KAKAO",
                "123456789",
                "ybc@example.com",
                "양배추",
                "10기",
                false
        );

        AuthTokenResponse tokenResponse = jwtTokenService.issueTokens(memberSummary);

        assertThatThrownBy(() -> jwtTokenService.reissueTokens(tokenResponse.accessToken()))
                .isInstanceOf(YbcException.class);
    }
}
