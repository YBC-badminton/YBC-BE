package com.ybc.ybc.global.config;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;

import java.io.InputStream;
import java.util.Properties;

@Configuration
public class MailConfig {
    @Bean
    @ConditionalOnProperty(name = "app.mail.stub-enabled", havingValue = "true")
    public JavaMailSender stubJavaMailSender(@Value("${app.mail.stub-delay-ms:0}") long stubDelayMs) {
        return new DelayedJavaMailSender(stubDelayMs);
    }

    private static final class DelayedJavaMailSender implements JavaMailSender {
        private final long stubDelayMs;

        private DelayedJavaMailSender(long stubDelayMs) {
            this.stubDelayMs = stubDelayMs;
        }

        @Override
        public MimeMessage createMimeMessage() {
            return new MimeMessage(Session.getInstance(new Properties()));
        }

        @Override
        public MimeMessage createMimeMessage(InputStream contentStream) {
            try {
                return new MimeMessage(Session.getInstance(new Properties()), contentStream);
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to create benchmark mime message", exception);
            }
        }

        @Override
        public void send(MimeMessage mimeMessage) {
            sleepIfNeeded();
        }

        @Override
        public void send(MimeMessage... mimeMessages) {
            sleepIfNeeded();
        }

        @Override
        public void send(MimeMessagePreparator mimeMessagePreparator) {
            sleepIfNeeded();
        }

        @Override
        public void send(MimeMessagePreparator... mimeMessagePreparators) {
            sleepIfNeeded();
        }

        @Override
        public void send(SimpleMailMessage simpleMessage) {
            sleepIfNeeded();
        }

        @Override
        public void send(SimpleMailMessage... simpleMessages) {
            sleepIfNeeded();
        }

        private void sleepIfNeeded() {
            if (stubDelayMs <= 0) {
                return;
            }

            try {
                Thread.sleep(stubDelayMs);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Benchmark mail stub interrupted", exception);
            }
        }
    }
}
