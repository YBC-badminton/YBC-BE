package com.ybc.ybc.support;

import com.ybc.ybc.applications.service.ApplicationSubmissionMailService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;

import static org.mockito.Mockito.mock;

@TestConfiguration
public class TestMailConfig {
    @Bean
    @Primary
    ApplicationSubmissionMailService applicationSubmissionMailService() {
        return mock(ApplicationSubmissionMailService.class);
    }

    @Bean
    @Primary
    JavaMailSender testJavaMailSender() {
        return mock(JavaMailSender.class);
    }
}
