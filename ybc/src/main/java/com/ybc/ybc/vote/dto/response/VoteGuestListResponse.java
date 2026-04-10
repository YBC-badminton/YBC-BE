package com.ybc.ybc.vote.dto.response;

import com.ybc.ybc.vote.domain.Guest;

import java.util.List;

public record VoteGuestListResponse(
        long totalGuestCount,
        List<VoteGuestSummaryResponse> guests
) {
    public static VoteGuestListResponse from(List<Guest> guests) {
        return new VoteGuestListResponse(
                guests.size(),
                guests.stream()
                        .map(VoteGuestSummaryResponse::from)
                        .toList()
        );
    }
}
