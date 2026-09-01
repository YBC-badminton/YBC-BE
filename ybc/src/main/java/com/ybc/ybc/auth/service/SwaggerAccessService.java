package com.ybc.ybc.auth.service;

import com.ybc.ybc.auth.domain.MemberPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class SwaggerAccessService {
    private static final Set<Long> SWAGGER_ALLOWED_MEMBER_IDS = Set.of(8L);

    public boolean canAccess(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        if (authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()))) {
            return true;
        }
        Long memberId = resolveMemberId(authentication);
        return memberId != null && SWAGGER_ALLOWED_MEMBER_IDS.contains(memberId);
    }

    private Long resolveMemberId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof MemberPrincipal memberPrincipal) {
            return memberPrincipal.getId();
        }
        if (principal instanceof Jwt jwt) {
            return parseMemberId(jwt.getSubject());
        }
        return null;
    }

    private Long parseMemberId(String subject) {
        if (subject == null || subject.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(subject);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
