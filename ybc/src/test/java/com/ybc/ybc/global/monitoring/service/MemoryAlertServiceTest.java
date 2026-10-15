package com.ybc.ybc.global.monitoring.service;

import com.ybc.ybc.global.monitoring.discord.DiscordWebhookClient;
import com.ybc.ybc.global.monitoring.incident.IncidentAlertTrigger;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MemoryAlertServiceTest {
    @Test void operationalWarningsKeepOriginalDiscordBeforeGptAndRecoveryDoesNotAnalyzeAgain() {
        var discord = mock(DiscordWebhookClient.class);
        var trigger = mock(IncidentAlertTrigger.class);
        var environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"test"});
        var alerts = new ErrorAlertService(discord, environment, trigger);
        for (String alert : new String[]{"DB_POOL_WAIT", "HIGH_REQUEST_LATENCY", "REPEATED_HTTP_5XX"}) {
            alerts.sendOperationalWarning(alert, "운영 경고", "observed evidence");
            var order = inOrder(discord, trigger);
            order.verify(discord).send(argThat(message -> message.contains("[YBC Operational Warning]") && message.contains(alert)));
            order.verify(trigger).submit(alert, "observed evidence");
            alerts.sendOperationalRecovery(alert, "recovery evidence");
            verify(discord).send(argThat(message -> message.contains("[YBC Operational Recovered]") && message.contains(alert)));
        }
        verify(trigger, times(3)).submit(anyString(), anyString());
        verifyNoMoreInteractions(trigger);
    }

    @Test void memoryWarningSendsOriginalAlertAndBoundedScopeEvidenceToAiWhileRecoveryOnlyNotifies() {
        var discord = mock(DiscordWebhookClient.class);
        var trigger = mock(IncidentAlertTrigger.class);
        var environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"test"});
        var alerts = new ErrorAlertService(discord, environment, trigger);
        alerts.sendMemoryWarningAlert(800_000_000, 800_000_000, null, "CGROUP_V2");
        var ordered = inOrder(discord, trigger);
        ordered.verify(discord).send(argThat(message -> message.contains("[YBC Memory Warning]")
                && message.contains("Used: 0.800 GB (800000000 bytes)") && message.contains("Cgroup Limit: UNKNOWN")));
        ordered.verify(trigger).submit(eq("HIGH_CONTAINER_MEMORY"), argThat(message -> message.contains("scope=CGROUP_V2")
                && message.contains("used=800000000 bytes") && message.contains("warningThreshold=800000000 bytes")
                && message.contains("cgroupLimit=UNKNOWN") && message.contains("JVM heap has a different scope")));
        alerts.sendMemoryRecoveredAlert(700_000_000, 700_000_000, "CGROUP_V2");
        verify(discord).send(argThat(message -> message.contains("[YBC Memory Recovered]") && message.contains("0.700 GB")));
        verifyNoMoreInteractions(trigger);
    }
}
