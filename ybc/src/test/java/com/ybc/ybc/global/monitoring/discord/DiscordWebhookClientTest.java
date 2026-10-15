package com.ybc.ybc.global.monitoring.discord;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.ybc.ybc.global.monitoring.config.MonitoringProperties;
import com.ybc.ybc.global.monitoring.incident.IncidentAlertTrigger;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class DiscordWebhookClientTest {
    private final MonitoringProperties settings = new MonitoringProperties(
            new MonitoringProperties.Discord(true, "https://discord.com/api/webhooks/1/test", "Monitor", ""), null, null);

    @Test void sendsSynchronouslyOnlyInRequestAndBoundsDeferredMessages() {
        var sent = new AtomicInteger(); Thread caller = Thread.currentThread();
        var client = new DiscordWebhookClient(settings, (uri, headers, body) -> {
            assertSame(caller, Thread.currentThread());
            assertTrue(IncidentAlertTrigger.isRequestActive());
            assertFalse(body.toString().contains("secret-value"));
            sent.incrementAndGet(); return JsonNodeFactory.instance.objectNode();
        });
        for (int i = 0; i < 25; i++) client.send("token=secret-value");
        assertEquals(0, sent.get());
        try (var scope = IncidentAlertTrigger.openRequest()) {
            client.flushPending(); assertEquals(2, sent.get());
            client.send("warning"); assertEquals(3, sent.get());
            for (int i = 0; i < 10; i++) client.flushPending();
        }
        assertEquals(21, sent.get());
        assertFalse(IncidentAlertTrigger.isRequestActive());
    }

    @Test void deliveryFailureDoesNotBreakBusinessRequest() {
        var client = new DiscordWebhookClient(settings, (uri, headers, body) -> { throw new IOException("offline"); });
        try (var scope = IncidentAlertTrigger.openRequest()) { assertDoesNotThrow(() -> client.send("warning")); }
    }
}
