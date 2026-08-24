package com.ybc.ybc.auth.domain;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;

class MemberPrincipalTest {

    @Test
    void fromJwtCreatesAdminAuthoritiesWhenClaimIsTrue() {
        Jwt jwt = Jwt.withTokenValue("admin-token")
                .header("alg", "HS256")
                .subject("1")
                .claim("provider", "KAKAO")
                .claim("socialId", "123456789")
                .claim("email", "admin@example.com")
                .claim("nickname", "운영진")
                .claim("isAdmin", true)
                .build();

        MemberPrincipal principal = MemberPrincipal.fromJwt(jwt);

        assertThat(principal.isAdmin()).isTrue();
        assertThat(principal.getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void fromJwtCreatesUserAuthorityWhenAdminClaimIsMissing() {
        Jwt jwt = Jwt.withTokenValue("member-token")
                .header("alg", "HS256")
                .subject("1")
                .claim("provider", "KAKAO")
                .claim("socialId", "123456789")
                .claim("email", "member@example.com")
                .claim("nickname", "일반회원")
                .build();

        MemberPrincipal principal = MemberPrincipal.fromJwt(jwt);

        assertThat(principal.isAdmin()).isFalse();
        assertThat(principal.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_USER");
    }
}
