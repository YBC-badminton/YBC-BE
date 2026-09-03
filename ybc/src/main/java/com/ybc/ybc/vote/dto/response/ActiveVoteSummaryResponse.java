package com.ybc.ybc.vote.dto.response;

import com.ybc.ybc.vote.repository.projection.ActiveVoteCandidateProjection;

public record ActiveVoteSummaryResponse(
        Long voteId,
        String name
) {
    public static ActiveVoteSummaryResponse from(ActiveVoteCandidateProjection projection) {
        return new ActiveVoteSummaryResponse(
                projection.voteId(),
                projection.name()
        );
    }
}
