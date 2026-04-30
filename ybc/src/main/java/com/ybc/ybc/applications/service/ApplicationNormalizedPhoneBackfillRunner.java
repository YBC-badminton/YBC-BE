package com.ybc.ybc.applications.service;

import com.ybc.ybc.applications.repository.ApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ApplicationNormalizedPhoneBackfillRunner implements ApplicationRunner {
    private final ApplicationRepository applicationRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        applicationRepository.backfillNormalizedPhone();
    }
}
