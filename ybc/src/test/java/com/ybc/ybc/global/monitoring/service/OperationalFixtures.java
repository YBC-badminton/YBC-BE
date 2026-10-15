package com.ybc.ybc.global.monitoring.service;

import com.ybc.ybc.global.monitoring.config.OperationalMonitoringProperties;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

final class OperationalFixtures {
    static OperationalMonitoringProperties settings() {
        return new OperationalMonitoringProperties(true, 10000, 60000, 1, 60, 300, 20, 1000, 800, 60, 3);
    }

    static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-10-14T00:00:00Z");
        void advance(long seconds) { now = now.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
