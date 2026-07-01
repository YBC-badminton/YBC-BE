package com.ybc.ybc.global.monitoring.service;

import com.ybc.ybc.global.monitoring.config.MonitoringProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public class DatabaseQuotaMonitor {
    private final JdbcTemplate jdbcTemplate;
    private final ErrorAlertService errorAlertService;
    private final MonitoringProperties monitoringProperties;
    private final AtomicLong databaseSizeBytes = new AtomicLong();
    private final AtomicReference<Double> databaseUsageRatio = new AtomicReference<>(0.0);
    private final AtomicReference<AlertLevel> lastAlertLevel = new AtomicReference<>(AlertLevel.NONE);
    private final AtomicReference<Boolean> queryFailed = new AtomicReference<>(false);

    public DatabaseQuotaMonitor(JdbcTemplate jdbcTemplate,
                                ErrorAlertService errorAlertService,
                                MonitoringProperties monitoringProperties,
                                MeterRegistry meterRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.errorAlertService = errorAlertService;
        this.monitoringProperties = monitoringProperties;

        Gauge.builder("ybc.supabase.database.size.bytes", databaseSizeBytes, AtomicLong::get)
                .description("Current PostgreSQL database size in bytes")
                .register(meterRegistry);
        Gauge.builder("ybc.supabase.database.usage.ratio", databaseUsageRatio, AtomicReference::get)
                .description("Current PostgreSQL database usage ratio against configured Supabase limit")
                .register(meterRegistry);
    }

    @Scheduled(
            fixedDelayString = "${app.monitoring.supabase.poll-interval-ms:1800000}",
            initialDelayString = "${app.monitoring.supabase.initial-delay-ms:60000}"
    )
    public void checkDatabaseSize() {
        MonitoringProperties.Supabase supabase = monitoringProperties.supabase();
        if (supabase == null || !supabase.enabled()) {
            return;
        }

        try {
            Long size = jdbcTemplate.queryForObject("select pg_database_size(current_database())", Long.class);
            if (size == null) {
                return;
            }

            queryFailed.set(false);
            databaseSizeBytes.set(size);

            long databaseLimitBytes = toBytes(supabase.dbSizeLimitMb());
            double usageRatio = size / (double) databaseLimitBytes;
            int usagePercent = (int) Math.round(usageRatio * 100);

            databaseUsageRatio.set(usageRatio);
            evaluateAlertLevel(size, databaseLimitBytes, usagePercent, supabase);
        } catch (Exception exception) {
            log.warn("Failed to query Supabase database size", exception);
            if (queryFailed.compareAndSet(false, true)) {
                errorAlertService.sendSupabaseMonitorFailureAlert(exception);
            }
        }
    }

    void evaluateAlertLevel(long databaseSizeBytes,
                            long databaseLimitBytes,
                            int usagePercent,
                            MonitoringProperties.Supabase supabase) {
        AlertLevel currentLevel = determineAlertLevel(usagePercent, supabase);
        AlertLevel previousLevel = lastAlertLevel.getAndSet(currentLevel);

        if (currentLevel == AlertLevel.NONE && previousLevel != AlertLevel.NONE) {
            errorAlertService.sendSupabaseQuotaRecoveredAlert(databaseSizeBytes, databaseLimitBytes, usagePercent);
            return;
        }

        if (currentLevel.ordinal() > previousLevel.ordinal()) {
            errorAlertService.sendSupabaseQuotaAlert(
                    databaseSizeBytes,
                    databaseLimitBytes,
                    usagePercent,
                    currentLevel.name()
            );
        }
    }

    static AlertLevel determineAlertLevel(int usagePercent, MonitoringProperties.Supabase supabase) {
        if (usagePercent >= supabase.emergencyThresholdPercent()) {
            return AlertLevel.EMERGENCY;
        }
        if (usagePercent >= supabase.criticalThresholdPercent()) {
            return AlertLevel.CRITICAL;
        }
        if (usagePercent >= supabase.warningThresholdPercent()) {
            return AlertLevel.WARNING;
        }
        return AlertLevel.NONE;
    }

    private long toBytes(long megabytes) {
        return megabytes * 1024 * 1024;
    }

    @Getter
    enum AlertLevel {
        NONE,
        WARNING,
        CRITICAL,
        EMERGENCY
    }
}
