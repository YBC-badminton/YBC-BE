package com.ybc.ybc.global.monitoring.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HttpRequestMonitorTest {
    private void record(HttpRequestWindow window, OperationalFixtures.TestClock clock, int count, int status, int latencyMs) {
        for (int i = 0; i < count; i++) window.record(status, TimeUnit.MILLISECONDS.toNanos(latencyMs), clock.instant().minusSeconds(1));
    }

    @Test void latencyRequiresTwentyRequestsAndAFullMinuteOfObservedBreach() {
        var settings = OperationalFixtures.settings();
        var window = new HttpRequestWindow(settings);
        var alerts = mock(ErrorAlertService.class);
        var clock = new OperationalFixtures.TestClock();
        var monitor = new HttpRequestMonitor(window, alerts, settings, clock);
        record(window, clock, 19, 200, 1000);
        for (int i = 0; i < 7; i++) { monitor.checkRequests(); clock.advance(10); }
        verifyNoInteractions(alerts);
        record(window, clock, 1, 200, 1000);
        for (int i = 0; i < 6; i++) { monitor.checkRequests(); clock.advance(10); }
        verifyNoInteractions(alerts);
        monitor.checkRequests();
        verify(alerts).sendOperationalWarning(eq("HIGH_REQUEST_LATENCY"), anyString(), argThat(text -> text.contains("requestCount=20")
                && text.contains("P95 range=[1000,1500) ms") && text.contains("Not an exact scalar P95")));
        clock.advance(10); monitor.checkRequests();
        verify(alerts, times(1)).sendOperationalWarning(anyString(), anyString(), anyString());
        record(window, clock, 381, 200, 100);
        clock.advance(10); monitor.checkRequests();
        verify(alerts).sendOperationalRecovery(eq("HIGH_REQUEST_LATENCY"), anyString());
    }

    @Test void repeated5xxAlertsAtThreeAndRearmsOnlyAfterObservedErrorFreeTraffic() {
        var settings = OperationalFixtures.settings();
        var window = new HttpRequestWindow(settings);
        var alerts = mock(ErrorAlertService.class);
        var clock = new OperationalFixtures.TestClock();
        var monitor = new HttpRequestMonitor(window, alerts, settings, clock);
        record(window, clock, 2, 500, 100);
        monitor.checkRequests(); verifyNoInteractions(alerts);
        record(window, clock, 1, 503, 100);
        monitor.checkRequests();
        verify(alerts).sendOperationalWarning(eq("REPEATED_HTTP_5XX"), anyString(), argThat(text -> text.contains("status5xxCount=3")));
        record(window, clock, 1, 502, 100);
        clock.advance(10); monitor.checkRequests();
        verify(alerts, times(1)).sendOperationalWarning(anyString(), anyString(), anyString());
        clock.advance(301); monitor.checkRequests();
        verify(alerts, never()).sendOperationalRecovery(anyString(), anyString());
        record(window, clock, 1, 200, 100);
        monitor.checkRequests();
        verify(alerts).sendOperationalRecovery(eq("REPEATED_HTTP_5XX"), anyString());
        record(window, clock, 3, 500, 100);
        monitor.checkRequests();
        verify(alerts, times(2)).sendOperationalWarning(eq("REPEATED_HTTP_5XX"), anyString(), anyString());
    }

    @Test void cpuPauseCannotBeCountedAsOneMinuteOfLatencyObservations() {
        var settings = OperationalFixtures.settings();
        var window = new HttpRequestWindow(settings);
        var alerts = mock(ErrorAlertService.class);
        var clock = new OperationalFixtures.TestClock();
        var monitor = new HttpRequestMonitor(window, alerts, settings, clock);
        record(window, clock, 20, 200, 1000);
        monitor.checkRequests(); clock.advance(60); monitor.checkRequests();
        verifyNoInteractions(alerts);
        for (int i = 0; i < 6; i++) { clock.advance(10); monitor.checkRequests(); }
        verify(alerts).sendOperationalWarning(eq("HIGH_REQUEST_LATENCY"), anyString(), anyString());
    }
}
