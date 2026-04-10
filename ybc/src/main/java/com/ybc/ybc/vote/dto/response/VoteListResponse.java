package com.ybc.ybc.vote.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

public record VoteListResponse(
        List<VoteSummaryResponse> votes,
        boolean isLast,
        long totalElements,
        int totalPages
) {
    public static VoteListResponse from(Page<VoteSummaryResponse> page) {
        return new VoteListResponse(
                page.getContent(),
                page.isLast(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
