package com.ybc.ybc.global.monitoring.service;

import com.ybc.ybc.global.monitoring.config.OperationalMonitoringProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

@Component
public class DatabasePoolMonitor {
    private final HikariPoolReader reader;
    private final ErrorAlertService alerts;
    private final OperationalMonitoringProperties settings;
    private final Clock clock;
    private final ThresholdAlertState state = new ThresholdAlertState();

    @Autowired
    public DatabasePoolMonitor(HikariPoolReader reader, ErrorAlertService alerts, OperationalMonitoringProperties settings) {
        this(reader, alerts, settings, Clock.systemUTC());
    }

    DatabasePoolMonitor(HikariPoolReader reader, ErrorAlertService alerts, OperationalMonitoringProperties settings, Clock clock) {
        this.reader = reader; this.alerts = alerts; this.settings = settings; this.clock = clock;
    }

    public void checkPool() {
        if (!settings.enabled()) return;
        var observed = reader.read();
        var condition = observed.map(sample -> sample.pending() >= settings.dbPendingThreshold()
                ? ThresholdAlertState.Condition.BREACH : sample.pending() == 0
                ? ThresholdAlertState.Condition.RECOVER : ThresholdAlertState.Condition.HOLD)
                .orElse(ThresholdAlertState.Condition.UNKNOWN);
        ThresholdAlertState.Transition transition;
        synchronized (this) {
            transition = state.evaluate(condition, clock.instant(), Duration.ofSeconds(settings.dbSustainSeconds()),
                    Duration.ofMillis(settings.pollIntervalMs() * 2));
        }
        if (transition == ThresholdAlertState.Transition.NONE || observed.isEmpty()) return;
        var sample = observed.get();
        String evidence = "Scope=this JVM primary Hikari pool, pool=" + sample.pool() + ", pending=" + sample.pending()
                + ", active=" + sample.active() + ", max=" + sample.maximum() + ", pendingThreshold=" + settings.dbPendingThreshold()
                + ", sustainSeconds=" + settings.dbSustainSeconds()
                + ". Sampled waiting threads; individual counters are not an atomic snapshot. This is not total DB server connections.";
        if (transition == ThresholdAlertState.Transition.WARNING) {
            alerts.sendOperationalWarning("DB_POOL_WAIT", "DB 연결을 기다리는 요청이 지속되고 있습니다.", evidence);
        } else alerts.sendOperationalRecovery("DB_POOL_WAIT", evidence);
    }
}
