package com.ybc.ybc.global.monitoring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.monitoring.operational")
public record OperationalMonitoringProperties(@DefaultValue("true") boolean enabled,
        @DefaultValue("10000") long pollIntervalMs, @DefaultValue("60000") long initialDelayMs,
        @DefaultValue("1") int dbPendingThreshold, @DefaultValue("60") long dbSustainSeconds,
        @DefaultValue("300") int httpWindowSeconds, @DefaultValue("20") int minimumLatencyRequests,
        @DefaultValue("1000") long latencyWarningMs, @DefaultValue("800") long latencyRecoveryMs,
        @DefaultValue("60") long latencySustainSeconds, @DefaultValue("3") int repeated5xxCount) {
    public OperationalMonitoringProperties {
        if (httpWindowSeconds < 1 || httpWindowSeconds > 3600 || pollIntervalMs < 1000 || initialDelayMs < 0
                || dbPendingThreshold < 1 || dbSustainSeconds < 0 || minimumLatencyRequests < 1 || repeated5xxCount < 1
                || latencyWarningMs < 1 || latencyWarningMs > 3_600_000 || latencyRecoveryMs < 0
                || latencyRecoveryMs >= latencyWarningMs || latencySustainSeconds < 0) {
            throw new IllegalArgumentException("운영 경보의 주기·시간 구간·요청 수·경고/복구 기준이 올바르지 않습니다.");
        }
    }
}
