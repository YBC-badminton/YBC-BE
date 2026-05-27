package com.ybc.ybc.vote.dto.response;

import com.ybc.ybc.applications.domain.Gender;
import com.ybc.ybc.vote.domain.Guest;

public record VoteGuestSummaryResponse(
        Long guestId,
        Long inviterId,
        String inviterName,
        String guestName,
        Gender gender,
        String level
) {
    public static VoteGuestSummaryResponse from(Guest guest) {
        return new VoteGuestSummaryResponse(
                guest.getId(),
                guest.getMember().getId(),
                guest.getMember().getNickname(),
                guest.getName(),
                guest.getGender(),
                guest.getLevel()
        );
    }
}
