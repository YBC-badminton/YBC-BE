package com.ybc.ybc.global.monitoring.discord;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.ybc.ybc.global.monitoring.config.MonitoringProperties;
import com.ybc.ybc.global.monitoring.incident.IncidentAlertTrigger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import com.ybc.ybc.global.monitoring.service.ErrorAlertService;
import com.ybc.ybc.global.monitoring.incident.IncidentHttp;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.HttpStatus;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.junit.jupiter.api.Assertions.*;

class DiscordWebhookClientTest {
    private final MonitoringProperties settings = new MonitoringProperties(
            new MonitoringProperties.Discord(true, "https://discord.com/api/webhooks/1/test", "Monitor", ""), null, null);

    @ParameterizedTest
    @EnumSource(IncidentHttp.QuotaFailure.class)
    void quotaFailureSendsActionableNativeAlertWithoutTriggeringGptAgain(IncidentHttp.QuotaFailure reason) {
        var payload = new AtomicReference<com.fasterxml.jackson.databind.JsonNode>();
        var client = new DiscordWebhookClient(settings, (uri, headers, body) -> {
            assertEquals("https://discord.com/api/webhooks/1/test", uri.toString());
            payload.set(body);
            return JsonNodeFactory.instance.objectNode();
        });
        var trigger = mock(IncidentAlertTrigger.class);
        var alerts = new ErrorAlertService(client, new MockEnvironment(), trigger);
        try (var scope = IncidentAlertTrigger.openRequest()) { alerts.sendIncidentQuotaAlert(reason); }
        String content = payload.get().path("content").asText();
        assertEquals("Monitor", payload.get().path("username").asText());
        assertTrue(content.contains("[YBC GPT Analysis Unavailable]"));
        assertTrue(content.contains(reason.name()));
        assertTrue(content.contains("OpenAI Platform → Settings → Organization → Billing"));
        assertTrue(content.contains("원본 오류·경고 알림은 별도로 유지"));
        assertTrue(content.contains("30분"));
        assertTrue(content.length() < 2000);
        if (reason == IncidentHttp.QuotaFailure.CREDIT_BALANCE_EXHAUSTED) assertTrue(content.contains("크레딧이 소진"));
        else assertTrue(content.contains("크레딧 또는 사용 한도 부족"));
        verifyNoInteractions(trigger);
    }

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
