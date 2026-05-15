package com.ybc.ybc.applications.service;

import com.ybc.ybc.applications.domain.Application;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ApplicationSubmissionMailService {
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ApplicationSubmissionEmailService applicationSubmissionEmailService;

    @Value("${app.mail.application.async-enabled:true}")
    private boolean asyncEnabled;

    public void sendApplicationSubmittedMail(Application application) {
        if (asyncEnabled) {
            applicationEventPublisher.publishEvent(new ApplicationSubmittedEvent(application));
            return;
        }

        applicationSubmissionEmailService.sendApplicationSubmittedMail(application);
    }
}
