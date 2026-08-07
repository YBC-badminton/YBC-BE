package com.ybc.ybc.auth.dto;

import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.members.domain.ClubMember;

public record MemberSummary(
        Long id,
        String provider,
        String socialId,
        String email,
        String nickname,
        String term,
        boolean isAdmin
) {
    public static MemberSummary from(Member member) {
        return from(member, null);
    }

    public static MemberSummary from(Member member, ClubMember clubMember) {
        return new MemberSummary(
                member.getId(),
                member.getSocialProvider().name(),
                member.getSocialId(),
                member.getEmail(),
                member.getNickname(),
                clubMember != null ? clubMember.getTerm() : null,
                member.isAdmin()
        );
    }
}
