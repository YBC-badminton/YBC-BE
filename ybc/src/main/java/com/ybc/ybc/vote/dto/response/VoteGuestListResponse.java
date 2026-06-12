package com.ybc.ybc.vote.dto.response;

import java.util.List;

public record VoteGuestListResponse(
        long totalGuestCount,
        long confirmedGuestCount,
        long waitingGuestCount,
        int guestQuota,
        List<VoteGuestSummaryResponse> guests
) {
    public static VoteGuestListResponse from(long confirmedGuestCount,
                                             long waitingGuestCount,
                                             int guestQuota,
                                             List<VoteGuestSummaryResponse> guests) {
        return new VoteGuestListResponse(
                guests.size(),
                confirmedGuestCount,
                waitingGuestCount,
                guestQuota,
                guests
        );
    }
}
