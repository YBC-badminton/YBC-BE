package com.ybc.ybc.auth.dto;

import com.ybc.ybc.vote.dto.response.ActiveVoteSummaryResponse;

import java.util.List;

public record CurrentMemberResponse(
        Long id,
        String provider,
        String socialId,
        String email,
        String nickname,
        String term,
        boolean isAdmin,
        List<ActiveVoteSummaryResponse> activeVotes
) {
    public static CurrentMemberResponse from(MemberSummary memberSummary,
                                             List<ActiveVoteSummaryResponse> activeVotes) {
        return new CurrentMemberResponse(
                memberSummary.id(),
                memberSummary.provider(),
                memberSummary.socialId(),
                memberSummary.email(),
                memberSummary.nickname(),
                memberSummary.term(),
                memberSummary.isAdmin(),
                List.copyOf(activeVotes)
        );
    }
}
