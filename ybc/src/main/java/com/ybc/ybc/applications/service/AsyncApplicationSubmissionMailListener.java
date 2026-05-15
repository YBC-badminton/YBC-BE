package com.ybc.ybc.applications.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class AsyncApplicationSubmissionMailListener {
    private final ApplicationSubmissionEmailService applicationSubmissionEmailService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ApplicationSubmittedEvent event) {
        applicationSubmissionEmailService.sendApplicationSubmittedMail(event.application());
    }
}
