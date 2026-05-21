package com.ybc.ybc.auth.dto;

import com.ybc.ybc.auth.domain.Member;

public record MemberSummary(
        Long id,
        String provider,
        String socialId,
        String email,
        String nickname,
        boolean isAdmin
) {
    public static MemberSummary from(Member member) {
        return new MemberSummary(
                member.getId(),
                member.getSocialProvider().name(),
                member.getSocialId(),
                member.getEmail(),
                member.getNickname(),
                member.isAdmin()
        );
    }
}
