package com.ybc.ybc.vote.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.vote.additional-reservation",
        name = "scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class VoteAdditionalReservationScheduler {
    private final AdminVoteService adminVoteService;

    @Scheduled(fixedDelayString = "${app.vote.additional-reservation.reconcile-delay-ms:60000}")
    public void reconcileAdditionalVotes() {
        adminVoteService.reconcileAdditionalVotes();
    }
}
