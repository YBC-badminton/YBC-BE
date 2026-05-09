package com.ybc.ybc.auth.controller;

import com.ybc.ybc.auth.config.KakaoOAuthProperties;
import com.ybc.ybc.auth.domain.MemberPrincipal;
import com.ybc.ybc.auth.dto.AuthTokenResponse;
import com.ybc.ybc.auth.dto.KakaoLoginRequest;
import com.ybc.ybc.auth.dto.KakaoLoginResponse;
import com.ybc.ybc.auth.dto.KakaoLoginUrlResponse;
import com.ybc.ybc.auth.dto.MemberResponse;
import com.ybc.ybc.auth.dto.MemberSummary;
import com.ybc.ybc.auth.service.AuthFacade;
import com.ybc.ybc.auth.dto.TokenRefreshRequest;
import com.ybc.ybc.auth.service.KakaoAuthService;
import com.ybc.ybc.auth.service.JwtTokenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Validated
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthFacade authFacade;
    private final KakaoAuthService kakaoAuthService;
    private final JwtTokenService jwtTokenService;
    private final KakaoOAuthProperties kakaoOAuthProperties;

    /**
     * GET /api/v1/auth/kakao/login-url
     * 카카오 로그인 페이지로 이동할 수 있는 인가 URL을 반환
     */
    @GetMapping("/kakao/login-url")
    public KakaoLoginUrlResponse getKakaoLoginUrl() {
        return new KakaoLoginUrlResponse("KAKAO", kakaoAuthService.getKakaoLoginUrl());
    }

    /**
     * POST /api/v1/auth/kakao/login
     * 인가 코드를 받아 카카오 로그인을 완료하고 JWT 발급
     */
    @PostMapping("/kakao/login")
    public KakaoLoginResponse loginWithKakao(@Valid @RequestBody KakaoLoginRequest request) {
        return authFacade.loginWithKakao(request.code());
    }

    /**
     * GET /api/v1/auth/kakao/callback
     * 카카오 콜백에서 전달된 인가 코드로 로그인 절차를 마무리하고 프론트엔드로 리다이렉트
     */
    @GetMapping("/kakao/callback")
    public ResponseEntity<Void> kakaoCallback(@RequestParam @NotBlank(message = "인가 코드가 필요합니다.") String code) {
        KakaoLoginResponse loginResponse = authFacade.loginWithKakao(code);
        URI redirectUri = UriComponentsBuilder.fromUriString(kakaoOAuthProperties.frontendRedirectUri())
                .queryParam("accessToken", loginResponse.tokens().accessToken())
                .queryParam("refreshToken", loginResponse.tokens().refreshToken())
                .queryParam("firstLogin", loginResponse.firstLogin())
                .build()
                .encode()
                .toUri();

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(redirectUri)
                .build();
    }

    /**
     * GET /api/v1/auth/me
     * 회원정보 조회
     */
    @GetMapping("/me")
    public MemberResponse getCurrentMember(@AuthenticationPrincipal MemberPrincipal memberPrincipal) {
        MemberSummary memberSummary = kakaoAuthService.getCurrentMember(memberPrincipal.getId());
        return MemberResponse.from(memberSummary);
    }


    /**
     * POST /api/v1/auth/refresh
     * 리프레시 토큰 발급
     */
    @PostMapping("/refresh")
    public AuthTokenResponse refresh(@Valid @RequestBody TokenRefreshRequest request) {
        return jwtTokenService.reissueTokens(request.refreshToken());
    }

    /**
     * POST /api/v1/auth/logout
     * 리프레시 토큰을 삭제해 로그아웃
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal MemberPrincipal memberPrincipal) {
        jwtTokenService.logout(memberPrincipal.getId());
        return ResponseEntity.noContent().build();
    }

}
