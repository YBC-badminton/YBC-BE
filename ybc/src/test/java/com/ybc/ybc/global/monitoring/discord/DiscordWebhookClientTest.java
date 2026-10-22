package com.ybc.ybc.global.monitoring.discord;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.ybc.ybc.global.monitoring.config.MonitoringProperties;
import com.ybc.ybc.global.monitoring.incident.IncidentAlertTrigger;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import com.ybc.ybc.global.monitoring.service.ErrorAlertService;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.HttpStatus;
import static org.mockito.Mockito.mock;
import static org.junit.jupiter.api.Assertions.*;

class DiscordWebhookClientTest {
    private final MonitoringProperties settings = new MonitoringProperties(
            new MonitoringProperties.Discord(true, "https://discord.com/api/webhooks/1/test", "Monitor", ""), null, null);

    @Test void serverErrorKeepsPublicErrorIdentifierWhileMaskingSecrets() {
        var content = new AtomicReference<String>();
        var client = new DiscordWebhookClient(settings, (uri, headers, body) -> {
            content.set(body.path("content").asText()); return JsonNodeFactory.instance.objectNode();
        });
        var alerts = new ErrorAlertService(client, new MockEnvironment(), mock(IncidentAlertTrigger.class));
        try (var scope = IncidentAlertTrigger.openRequest()) {
            alerts.sendServerErrorAlert(new MockHttpServletRequest("POST", "/api/v1/auth/refresh"),
                    HttpStatus.SERVICE_UNAVAILABLE, "REFRESH_TOKEN_STORE_UNAVAILABLE", new RuntimeException("token=secret-value"));
        }
        assertTrue(content.get().contains("Error Type: REFRESH_TOKEN_STORE_UNAVAILABLE"));
        assertTrue(content.get().contains("URI: /api/v1/auth/refresh"));
        assertFalse(content.get().contains("secret-value"));
    }

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
