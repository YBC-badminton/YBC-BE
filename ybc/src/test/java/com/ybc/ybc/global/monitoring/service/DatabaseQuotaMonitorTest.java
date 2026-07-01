package com.ybc.ybc.global.monitoring.service;

import com.ybc.ybc.global.monitoring.config.MonitoringProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseQuotaMonitorTest {

    @Test
    void determineAlertLevelReturnsExpectedSeverity() {
        MonitoringProperties.Supabase supabase = new MonitoringProperties.Supabase(
                true,
                1_800_000L,
                60_000L,
                500L,
                70,
                85,
                95
        );

        assertThat(DatabaseQuotaMonitor.determineAlertLevel(65, supabase)).isEqualTo(DatabaseQuotaMonitor.AlertLevel.NONE);
        assertThat(DatabaseQuotaMonitor.determineAlertLevel(70, supabase)).isEqualTo(DatabaseQuotaMonitor.AlertLevel.WARNING);
        assertThat(DatabaseQuotaMonitor.determineAlertLevel(85, supabase)).isEqualTo(DatabaseQuotaMonitor.AlertLevel.CRITICAL);
        assertThat(DatabaseQuotaMonitor.determineAlertLevel(95, supabase)).isEqualTo(DatabaseQuotaMonitor.AlertLevel.EMERGENCY);
    }
}
