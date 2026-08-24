package com.ybc.ybc.auth.domain;

import com.ybc.ybc.auth.dto.MemberSummary;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;

@Getter
@RequiredArgsConstructor
public class MemberPrincipal implements UserDetails, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String provider;
    private final String socialId;
    private final String email;
    private final String nickname;
    private final boolean isAdmin;

    public static MemberPrincipal from(MemberSummary memberSummary) {
        return new MemberPrincipal(
                memberSummary.id(),
                memberSummary.provider(),
                memberSummary.socialId(),
                memberSummary.email(),
                memberSummary.nickname(),
                memberSummary.isAdmin()
        );
    }

    public static MemberPrincipal fromJwt(Jwt jwt) {
        return new MemberPrincipal(
                Long.parseLong(jwt.getSubject()),
                jwt.getClaimAsString("provider"),
                jwt.getClaimAsString("socialId"),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("nickname"),
                Boolean.TRUE.equals(jwt.getClaim("isAdmin"))
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (isAdmin) {
            return List.of(
                    new SimpleGrantedAuthority("ROLE_USER"),
                    new SimpleGrantedAuthority("ROLE_ADMIN")
            );
        }
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return String.valueOf(id);
    }
}
