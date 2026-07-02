package com.ybc.ybc.global.monitoring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.monitoring")
public record MonitoringProperties(
        Discord discord,
        Supabase supabase,
        TestEndpoint testEndpoint
) {
    public record Discord(
            boolean enabled,
            String webhookUrl,
            String username,
            String mention
    ) {
    }

    public record Supabase(
            boolean enabled,
            long pollIntervalMs,
            long initialDelayMs,
            long dbSizeLimitMb,
            int warningThresholdPercent,
            int criticalThresholdPercent,
            int emergencyThresholdPercent
    ) {
    }

    public record TestEndpoint(
            boolean enabled,
            String token
    ) {
    }
}
