package com.ybc.ybc.auth.dto;

public record MemberResponse(
        Long id,
        String provider,
        String socialId,
        String email,
        String nickname
) {
    public static MemberResponse from(MemberSummary memberSummary) {
        return new MemberResponse(
                memberSummary.id(),
                memberSummary.provider(),
                memberSummary.socialId(),
                memberSummary.email(),
                memberSummary.nickname()
        );
    }
}
