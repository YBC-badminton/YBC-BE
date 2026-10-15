package com.ybc.ybc.global.monitoring.service;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DatabasePoolMonitorTest {
    @Test void alertsAfterSampledWaitPersistsForOneMinuteAndRearmsAfterNoWaitingThreads() {
        var reader = mock(HikariPoolReader.class);
        var alerts = mock(ErrorAlertService.class);
        var clock = new OperationalFixtures.TestClock();
        var monitor = new DatabasePoolMonitor(reader, alerts, OperationalFixtures.settings(), clock);
        when(reader.read()).thenReturn(Optional.of(new HikariPoolReader.Sample("main", 1, 15, 15)));
        for (int i = 0; i < 6; i++) { monitor.checkPool(); clock.advance(10); }
        verifyNoInteractions(alerts);
        monitor.checkPool();
        verify(alerts).sendOperationalWarning(eq("DB_POOL_WAIT"), anyString(), argThat(text -> text.contains("pool=main")
                && text.contains("pending=1") && text.contains("active=15") && text.contains("max=15")));
        clock.advance(10); monitor.checkPool();
        verify(alerts, times(1)).sendOperationalWarning(anyString(), anyString(), anyString());
        when(reader.read()).thenReturn(Optional.of(new HikariPoolReader.Sample("main", 0, 15, 15)));
        clock.advance(10); monitor.checkPool();
        verify(alerts).sendOperationalRecovery(eq("DB_POOL_WAIT"), anyString());
        // A full pool alone, with no waiters, must not be described as pool exhaustion.
        clock.advance(10); monitor.checkPool();
        verifyNoMoreInteractions(alerts);
    }

    @Test void briefWaitMissingDataAndLongCpuPauseCannotProveSustainedWaiting() {
        var reader = mock(HikariPoolReader.class);
        var alerts = mock(ErrorAlertService.class);
        var clock = new OperationalFixtures.TestClock();
        var monitor = new DatabasePoolMonitor(reader, alerts, OperationalFixtures.settings(), clock);
        var pending = Optional.of(new HikariPoolReader.Sample("main", 1, 15, 15));
        when(reader.read()).thenReturn(pending);
        monitor.checkPool(); clock.advance(50); monitor.checkPool();
        clock.advance(10); monitor.checkPool();
        verifyNoInteractions(alerts);
        when(reader.read()).thenReturn(Optional.empty());
        clock.advance(10); monitor.checkPool();
        when(reader.read()).thenReturn(pending);
        clock.advance(10); monitor.checkPool();
        for (int i = 0; i < 5; i++) { clock.advance(10); monitor.checkPool(); }
        verifyNoInteractions(alerts);
        clock.advance(10); monitor.checkPool();
        verify(alerts).sendOperationalWarning(eq("DB_POOL_WAIT"), anyString(), anyString());
        when(reader.read()).thenReturn(Optional.empty());
        clock.advance(10); monitor.checkPool();
        verify(alerts, never()).sendOperationalRecovery(anyString(), anyString());
    }
}
