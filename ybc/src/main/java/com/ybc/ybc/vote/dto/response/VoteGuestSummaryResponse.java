package com.ybc.ybc.vote.dto.response;

import com.ybc.ybc.applications.domain.Gender;
import com.ybc.ybc.vote.domain.Guest;

public record VoteGuestSummaryResponse(
        Long guestId,
        Long voteId,
        Long inviterId,
        String inviterName,
        String guestName,
        Gender gender,
        String level,
        boolean isWaiting
) {
    public static VoteGuestSummaryResponse from(Guest guest, boolean isWaiting) {
        return new VoteGuestSummaryResponse(
                guest.getId(),
                guest.getVote().getId(),
                guest.getMember().getId(),
                guest.getMember().getNickname(),
                guest.getName(),
                guest.getGender(),
                guest.getLevel(),
                isWaiting
        );
    }
}
