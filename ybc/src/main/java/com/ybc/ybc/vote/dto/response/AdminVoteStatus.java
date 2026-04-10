package com.ybc.ybc.vote.dto.response;

import java.time.LocalDateTime;

public enum AdminVoteStatus {
    UPCOMING,
    IN_PROGRESS,
    COMPLETED;

    public static AdminVoteStatus from(LocalDateTime voteStartAt, LocalDateTime voteEndAt, LocalDateTime now) {
        if (now.isBefore(voteStartAt)) {
            return UPCOMING;
        }
        if (now.isBefore(voteEndAt)) {
            return IN_PROGRESS;
        }
        return COMPLETED;
    }
}
