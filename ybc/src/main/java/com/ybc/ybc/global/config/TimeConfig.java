package com.ybc.ybc.global.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.TimeZone;

@Configuration
public class TimeConfig {
    private static final ZoneId ASIA_SEOUL = ZoneId.of("Asia/Seoul");

    @PostConstruct
    void setDefaultTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(ASIA_SEOUL));
    }

    @Bean
    public Clock systemClock() {
        return Clock.system(ASIA_SEOUL);
    }

    @Bean
    public DateTimeProvider auditingDateTimeProvider(Clock systemClock) {
        return () -> Optional.of(LocalDateTime.now(systemClock));
    }
}
