package com.ybc.ybc.global.monitoring.service;

import com.ybc.ybc.global.monitoring.config.MemoryMonitoringProperties;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MemoryUsageMonitorTest {
    private Optional<ContainerMemoryReader.Sample> sample(long bytes) {
        return Optional.of(new ContainerMemoryReader.Sample(bytes, 1_073_741_824L, "CGROUP_V2"));
    }

    @Test void warnsAtPointEightGbOnceAndRearmsOnlyAfterPointSevenGbRecovery() {
        var reader = mock(ContainerMemoryReader.class);
        var alerts = mock(ErrorAlertService.class);
        var monitor = new MemoryUsageMonitor(reader, alerts, new MemoryMonitoringProperties(true, 800_000_000, 700_000_000));
        when(reader.read()).thenReturn(sample(799_999_999), sample(800_000_000), sample(900_000_000),
                sample(750_000_000), sample(799_999_999), sample(700_000_000), sample(650_000_000), sample(800_000_000));
        monitor.checkMemory();
        verifyNoInteractions(alerts);
        for (int i = 0; i < 4; i++) monitor.checkMemory();
        verify(alerts).sendMemoryWarningAlert(800_000_000, 800_000_000, 1_073_741_824L, "CGROUP_V2");
        verify(alerts, never()).sendMemoryRecoveredAlert(anyLong(), anyLong(), anyString());
        monitor.checkMemory();
        verify(alerts).sendMemoryRecoveredAlert(700_000_000, 700_000_000, "CGROUP_V2");
        monitor.checkMemory();
        monitor.checkMemory();
        verify(alerts, times(2)).sendMemoryWarningAlert(800_000_000, 800_000_000, 1_073_741_824L, "CGROUP_V2");
        verifyNoMoreInteractions(alerts);
    }

    @Test void unavailableSamplesDoNotMeanRecoveryOrDuplicateWarning() {
        var reader = mock(ContainerMemoryReader.class);
        var alerts = mock(ErrorAlertService.class);
        var monitor = new MemoryUsageMonitor(reader, alerts, new MemoryMonitoringProperties(true, 800_000_000, 700_000_000));
        when(reader.read()).thenReturn(sample(800_000_000), Optional.empty(), Optional.empty(), sample(900_000_000), sample(700_000_000));
        for (int i = 0; i < 5; i++) monitor.checkMemory();
        verify(alerts).sendMemoryWarningAlert(800_000_000, 800_000_000, 1_073_741_824L, "CGROUP_V2");
        verify(alerts).sendMemoryRecoveredAlert(700_000_000, 700_000_000, "CGROUP_V2");
        verifyNoMoreInteractions(alerts);
    }

    @Test void disabledMonitorDoesNotReadMemoryAndInvalidThresholdsFailClearly() {
        var reader = mock(ContainerMemoryReader.class);
        var alerts = mock(ErrorAlertService.class);
        new MemoryUsageMonitor(reader, alerts, new MemoryMonitoringProperties(false, 0, 0)).checkMemory();
        verifyNoInteractions(reader, alerts);
        assertThrows(IllegalArgumentException.class, () -> new MemoryMonitoringProperties(true, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new MemoryMonitoringProperties(true, 800_000_000, 800_000_000));
        assertThrows(IllegalArgumentException.class, () -> new MemoryMonitoringProperties(true, 800_000_000, -1));
    }
}
