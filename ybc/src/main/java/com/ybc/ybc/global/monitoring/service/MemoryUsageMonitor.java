package com.ybc.ybc.global.monitoring.service;

import com.ybc.ybc.global.monitoring.config.MemoryMonitoringProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/** One warning per threshold crossing, rearmed only after the lower recovery threshold. */
@Slf4j
@Component
@EnableConfigurationProperties(MemoryMonitoringProperties.class)
public class MemoryUsageMonitor {
    private final ContainerMemoryReader reader;
    private final ErrorAlertService alerts;
    private final MemoryMonitoringProperties settings;
    private boolean warningActive;
    private boolean unavailableLogged;
    private boolean limitMismatchLogged;

    public MemoryUsageMonitor(ContainerMemoryReader reader, ErrorAlertService alerts, MemoryMonitoringProperties settings) {
        this.reader = reader; this.alerts = alerts; this.settings = settings;
    }

    public void checkMemory() {
        Runnable notification = null;
        synchronized (this) {
            if (!settings.enabled()) return;
            var observed = reader.read();
            if (observed.isEmpty()) {
                if (!unavailableLogged) log.warn("Container memory accounting unavailable; memory alerts cannot be evaluated. JVM/host memory is not substituted.");
                unavailableLogged = true;
                return;
            }
            unavailableLogged = false;
            var sample = observed.get();
            if (sample.limitBytes() != null && settings.warningThresholdBytes() >= sample.limitBytes() && !limitMismatchLogged) {
                log.warn("Memory warning threshold is at or above the cgroup limit; an earlier warning requires a lower threshold.");
                limitMismatchLogged = true;
            }
            if (!warningActive && sample.usedBytes() >= settings.warningThresholdBytes()) {
                notification = () -> alerts.sendMemoryWarningAlert(sample.usedBytes(), settings.warningThresholdBytes(), sample.limitBytes(), sample.scope());
                warningActive = true;
            } else if (warningActive && sample.usedBytes() <= settings.recoveryThresholdBytes()) {
                notification = () -> alerts.sendMemoryRecoveredAlert(sample.usedBytes(), settings.recoveryThresholdBytes(), sample.scope());
                warningActive = false;
            }
        }
        if (notification != null) notification.run();
    }
}
