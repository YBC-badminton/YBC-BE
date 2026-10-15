package com.ybc.ybc.global.monitoring.service;

import com.ybc.ybc.global.monitoring.config.OperationalMonitoringProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OperationalSettingsTest {
    private OperationalMonitoringProperties bind(Map<String, String> values) {
        return new Binder(new MapConfigurationPropertySource(values)).bindOrCreate("app.monitoring.operational", OperationalMonitoringProperties.class);
    }

    @Test void defaultSettingsWorkWithoutYamlAndOptionalThresholdOverridesBindCorrectly() {
        var defaults = bind(Map.of());
        assertTrue(defaults.enabled());
        assertEquals(20, defaults.minimumLatencyRequests());
        assertEquals(3, defaults.repeated5xxCount());
        assertEquals(300, defaults.httpWindowSeconds());
        var changed = bind(Map.of("app.monitoring.operational.repeated5xx-count", "5",
                "app.monitoring.operational.latency-warning-ms", "1500"));
        assertEquals(5, changed.repeated5xxCount());
        assertEquals(1500, changed.latencyWarningMs());
    }

    @Test void disablingMonitoringStopsBothCollectionAndPoolAndHttpEvaluation() {
        var settings = bind(Map.of("app.monitoring.operational.enabled", "false"));
        var window = new HttpRequestWindow(settings);
        window.record(500, 1, Instant.now());
        assertEquals(0, window.snapshot(Instant.now().plusSeconds(1)).requests());
        var reader = mock(HikariPoolReader.class);
        var alerts = mock(ErrorAlertService.class);
        new DatabasePoolMonitor(reader, alerts, settings).checkPool();
        new HttpRequestMonitor(window, alerts, settings).checkRequests();
        verifyNoInteractions(reader, alerts);
    }
}
