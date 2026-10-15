package com.ybc.ybc.global.monitoring.service;

import com.ybc.ybc.global.monitoring.config.OperationalMonitoringProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

@Component
public class HttpRequestMonitor {
    private final HttpRequestWindow window;
    private final ErrorAlertService alerts;
    private final OperationalMonitoringProperties settings;
    private final Clock clock;
    private final ThresholdAlertState latency = new ThresholdAlertState();
    private final ThresholdAlertState errors = new ThresholdAlertState();

    @Autowired
    public HttpRequestMonitor(HttpRequestWindow window, ErrorAlertService alerts, OperationalMonitoringProperties settings) {
        this(window, alerts, settings, Clock.systemUTC());
    }

    HttpRequestMonitor(HttpRequestWindow window, ErrorAlertService alerts, OperationalMonitoringProperties settings, Clock clock) {
        this.window = window; this.alerts = alerts; this.settings = settings; this.clock = clock;
    }

    public void checkRequests() {
        if (!settings.enabled()) return;
        var now = clock.instant();
        var sample = window.snapshot(now);
        Duration maximumGap = Duration.ofMillis(settings.pollIntervalMs() * 2);
        var latencyCondition = ThresholdAlertState.Condition.UNKNOWN;
        if (sample.requests() >= settings.minimumLatencyRequests() && sample.p95LowerMs() != null) {
            latencyCondition = sample.p95LowerMs() >= settings.latencyWarningMs() ? ThresholdAlertState.Condition.BREACH
                    : sample.p95UpperExclusiveMs() != null && sample.p95UpperExclusiveMs() <= settings.latencyRecoveryMs()
                    ? ThresholdAlertState.Condition.RECOVER : ThresholdAlertState.Condition.HOLD;
        }
        var errorCondition = sample.errors5xx() >= settings.repeated5xxCount() ? ThresholdAlertState.Condition.BREACH
                : sample.requests() > 0 && sample.errors5xx() == 0 ? ThresholdAlertState.Condition.RECOVER
                : sample.requests() == 0 ? ThresholdAlertState.Condition.UNKNOWN : ThresholdAlertState.Condition.HOLD;
        ThresholdAlertState.Transition latencyTransition, errorTransition;
        synchronized (this) {
            latencyTransition = latency.evaluate(latencyCondition, now, Duration.ofSeconds(settings.latencySustainSeconds()), maximumGap);
            errorTransition = errors.evaluate(errorCondition, now, Duration.ZERO, maximumGap);
        }
        String scope = "Scope=this JVM completed HTTP requests, windowStart=" + sample.start() + ", windowEnd=" + sample.end()
                + ", requestCount=" + sample.requests() + ", status5xxCount=" + sample.errors5xx()
                + ". One-second buckets; actuator/internal requests excluded. Other instances and platform-level errors are not counted.";
        if (latencyTransition != ThresholdAlertState.Transition.NONE) {
            String evidence = scope + " P95 range=[" + sample.p95LowerMs() + ","
                    + (sample.p95UpperExclusiveMs() == null ? "UNBOUNDED" : sample.p95UpperExclusiveMs())
                    + ") ms, method=nearest-rank histogram interval, warningMs=" + settings.latencyWarningMs()
                    + ", recoveryMs=" + settings.latencyRecoveryMs() + ", minimumRequests=" + settings.minimumLatencyRequests()
                    + ", sustainSeconds=" + settings.latencySustainSeconds() + ". Not an exact scalar P95 or proof of a specific cause.";
            if (latencyTransition == ThresholdAlertState.Transition.WARNING) {
                alerts.sendOperationalWarning("HIGH_REQUEST_LATENCY", "최근 요청의 P95 응답 지연이 경고 기준 이상으로 지속됩니다.", evidence);
            } else alerts.sendOperationalRecovery("HIGH_REQUEST_LATENCY", evidence);
        }
        if (errorTransition != ThresholdAlertState.Transition.NONE) {
            String evidence = scope + " Warning requires status5xxCount >= " + settings.repeated5xxCount()
                    + ". Recovery means no 5xx in this window with observed requests; it does not prove full service health.";
            if (errorTransition == ThresholdAlertState.Transition.WARNING) {
                alerts.sendOperationalWarning("REPEATED_HTTP_5XX", "최근 요청에서 서버 오류가 반복되고 있습니다.", evidence);
            } else alerts.sendOperationalRecovery("REPEATED_HTTP_5XX", evidence);
        }
    }
}
