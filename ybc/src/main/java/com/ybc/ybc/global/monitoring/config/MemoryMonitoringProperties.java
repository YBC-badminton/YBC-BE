package com.ybc.ybc.global.monitoring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.monitoring.memory")
public record MemoryMonitoringProperties(boolean enabled, long warningThresholdBytes, long recoveryThresholdBytes) {
    public MemoryMonitoringProperties {
        if (enabled && (warningThresholdBytes <= 0 || recoveryThresholdBytes < 0 || recoveryThresholdBytes >= warningThresholdBytes)) {
            throw new IllegalArgumentException("메모리 복구 기준은 0 이상이며 경고 기준보다 작아야 합니다.");
        }
    }
}
