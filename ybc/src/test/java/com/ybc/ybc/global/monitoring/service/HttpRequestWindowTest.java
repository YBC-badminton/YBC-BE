package com.ybc.ybc.global.monitoring.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class HttpRequestWindowTest {
    private final Instant now = Instant.parse("2026-10-14T00:05:00Z");

    @Test void countsShareCompletedWindowAndCurrentPartialSecondIsExcluded() {
        var window = new HttpRequestWindow(OperationalFixtures.settings());
        window.record(500, 1, now.minusSeconds(300));
        window.record(503, 1, now.minusSeconds(301));
        window.record(429, 1, now.minusSeconds(1));
        window.record(502, 1, now.plusMillis(100));
        var sample = window.snapshot(now.plusMillis(900));
        assertEquals(now.minusSeconds(300), sample.start());
        assertEquals(now, sample.end());
        assertEquals(2, sample.requests());
        assertEquals(1, sample.errors5xx());
        assertEquals(2, window.snapshot(now.plusSeconds(1)).requests());
        assertEquals(1, window.snapshot(now.plusSeconds(1)).errors5xx());
    }

    @Test void p95IsABoundedNearestRankIntervalAndThresholdEqualityIsPreserved() {
        var window = new HttpRequestWindow(OperationalFixtures.settings());
        for (int i = 0; i < 19; i++) window.record(200, TimeUnit.MILLISECONDS.toNanos(100), now.minusSeconds(1));
        window.record(200, TimeUnit.MILLISECONDS.toNanos(1500), now.minusSeconds(1));
        assertEquals(100L, window.snapshot(now).p95LowerMs());
        // The 19th of 20 observations is still fast. One slow request alone is not the P95.
        window.record(200, TimeUnit.MILLISECONDS.toNanos(1000), now.minusSeconds(1));
        assertEquals(1000L, window.snapshot(now).p95LowerMs());
        assertEquals(1500L, window.snapshot(now).p95UpperExclusiveMs());
        assertNull(window.snapshot(now.plusSeconds(301)).p95LowerMs());
    }

    @Test void longLatencyTailIsUnboundedRatherThanInventingAnExactP95() {
        var window = new HttpRequestWindow(OperationalFixtures.settings());
        window.record(200, TimeUnit.MINUTES.toNanos(5), now.minusSeconds(1));
        assertEquals(60000L, window.snapshot(now).p95LowerMs());
        assertNull(window.snapshot(now).p95UpperExclusiveMs());
    }
}
