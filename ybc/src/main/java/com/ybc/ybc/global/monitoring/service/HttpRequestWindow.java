package com.ybc.ybc.global.monitoring.service;

import com.ybc.ybc.global.monitoring.config.OperationalMonitoringProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Arrays;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

/** Bounded one-second buckets: no request bodies, paths, users, or individual request samples are retained. */
@Component
public class HttpRequestWindow {
    private final OperationalMonitoringProperties settings;
    private final long[] lowerBoundsNanos;
    private final Bucket[] buckets;

    public HttpRequestWindow(OperationalMonitoringProperties settings) {
        this.settings = settings;
        TreeSet<Long> bounds = new TreeSet<>();
        for (long ms : new long[]{0, 50, 100, 200, 300, 500, 800, 1000, 1500, 2000, 3000, 5000, 10000, 30000, 60000}) bounds.add(ms);
        bounds.add(settings.latencyWarningMs());
        bounds.add(settings.latencyRecoveryMs());
        lowerBoundsNanos = bounds.stream().mapToLong(TimeUnit.MILLISECONDS::toNanos).toArray();
        buckets = new Bucket[settings.httpWindowSeconds() + 1];
        for (int i = 0; i < buckets.length; i++) buckets[i] = new Bucket(lowerBoundsNanos.length);
    }

    synchronized void record(int status, long durationNanos, Instant completedAt) {
        if (!settings.enabled() || durationNanos < 0 || status < 100 || status > 599) return;
        long second = completedAt.getEpochSecond();
        Bucket bucket = buckets[Math.floorMod(second, buckets.length)];
        if (bucket.second != second) {
            bucket.second = second; bucket.requests = 0; bucket.errors = 0;
            Arrays.fill(bucket.durations, 0);
        }
        int index = Arrays.binarySearch(lowerBoundsNanos, durationNanos);
        if (index < 0) index = -index - 2;
        bucket.requests++;
        if (status >= 500) bucket.errors++;
        bucket.durations[index]++;
    }

    public synchronized Snapshot snapshot(Instant now) {
        // Exclude the current partial second so all counts share exactly the same completed interval.
        long end = now.getEpochSecond();
        long start = end - settings.httpWindowSeconds();
        long requests = 0, errors = 0;
        long[] durations = new long[lowerBoundsNanos.length];
        for (Bucket bucket : buckets) {
            if (bucket.second < start || bucket.second >= end) continue;
            requests += bucket.requests; errors += bucket.errors;
            for (int i = 0; i < durations.length; i++) durations[i] += bucket.durations[i];
        }
        Long lower = null, upper = null;
        if (requests > 0) {
            long rank = requests - requests / 20; // ceil(0.95 * requests), without floating point rounding.
            long cumulative = 0;
            for (int i = 0; i < durations.length; i++) {
                cumulative += durations[i];
                if (cumulative >= rank) {
                    lower = TimeUnit.NANOSECONDS.toMillis(lowerBoundsNanos[i]);
                    if (i + 1 < lowerBoundsNanos.length) upper = TimeUnit.NANOSECONDS.toMillis(lowerBoundsNanos[i + 1]);
                    break;
                }
            }
        }
        return new Snapshot(Instant.ofEpochSecond(start), Instant.ofEpochSecond(end), requests, errors, lower, upper);
    }

    private static final class Bucket {
        long second = Long.MIN_VALUE, requests, errors;
        final long[] durations;
        Bucket(int bins) { durations = new long[bins]; }
    }

    public record Snapshot(Instant start, Instant end, long requests, long errors5xx, Long p95LowerMs, Long p95UpperExclusiveMs) { }
}
