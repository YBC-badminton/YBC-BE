package com.ybc.ybc.global.monitoring.incident;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ybc.ybc.global.monitoring.discord.DiscordWebhookClient;
import com.ybc.ybc.global.monitoring.service.ErrorAlertService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;

import static com.ybc.ybc.global.monitoring.incident.IncidentJson.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IncidentAlertTriggerTest {
    private final MockEnvironment environment = new MockEnvironment()
            .withProperty("app.monitoring.incident.enabled", "true")
            .withProperty("app.monitoring.incident.discord-webhook-url", "https://discord.com/api/webhooks/1/test");
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-14T00:00:00Z"), ZoneOffset.UTC);

    @Test void concurrentRequestsDeferSecondAnalysisWithoutBlockingItsBusinessThread() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var analyzer = new IncidentAnalyzer((uri, headers, body) -> {
            if (calls.incrementAndGet() == 1) {
                entered.countDown();
                assertTrue(release.await(5, TimeUnit.SECONDS));
            }
            return IncidentFixtures.response(diagnosis(parse(body.path("input").asText())));
        }, "key", "gpt-4.1-mini");
        var trigger = new IncidentAlertTrigger(environment, analyzer, (report, uri) -> {}, clock);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var first = executor.submit(() -> {
                try (var scope = IncidentAlertTrigger.openRequest()) { trigger.submit("FIRST", "first"); }
            });
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                try (var scope = IncidentAlertTrigger.openRequest()) { trigger.submit("SECOND", "second"); }
                assertEquals(1, calls.get());
            } finally { release.countDown(); }
            first.get(5, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
        try (var scope = IncidentAlertTrigger.openRequest()) { trigger.flushPending(); }
        assertEquals(2, calls.get());
    }

    @Test void fiveMinuteCooldownIsMeasuredFromAttemptAndLogBoundaryRemainsValid() throws Exception {
        var current = new AtomicReference<>(clock.instant());
        Clock advancing = new Clock() {
            public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(java.time.ZoneId zone) { return this; }
            public Instant instant() { return current.get(); }
        };
        var inputs = new ArrayList<JsonNode>();
        var analyzer = new IncidentAnalyzer((uri, headers, body) -> {
            var input = parse(body.path("input").asText()); inputs.add(input);
            return IncidentFixtures.response(diagnosis(input));
        }, "key", "gpt-4.1-mini");
        var trigger = new IncidentAlertTrigger(environment, analyzer, (report, uri) -> {}, advancing);
        try (var scope = IncidentAlertTrigger.openRequest()) { trigger.submit("ERROR", "old"); }
        current.set(clock.instant().plusSeconds(299));
        try (var scope = IncidentAlertTrigger.openRequest()) { trigger.submit("ERROR", "within cooldown"); }
        assertEquals(1, inputs.size());
        current.set(clock.instant().plusSeconds(300));
        try (var scope = IncidentAlertTrigger.openRequest()) { trigger.submit("ERROR", "new"); }
        assertEquals(2, inputs.size());
        assertEquals(2, inputs.get(1).path("relatedLogs").size());
        assertFalse(inputs.get(1).toString().contains("\"old\""));
    }

    private static ObjectNode diagnosis(JsonNode input) {
        var output = IncidentFixtures.diagnosis();
        var candidate = (ObjectNode) output.path("causeCandidates").get(0);
        candidate.put("code", "APPLICATION_ERROR");
        candidate.putArray("evidenceFields").add("/relatedLogs/0/message");
        ((ObjectNode) candidate.path("confidence")).put("level", "LOW");
        ((ObjectNode) output.path("confidence")).put("level", "LOW");
        output.putArray("evidence").addObject().put("field", "/relatedLogs/0/message")
                .put("value", input.at("/relatedLogs/0/message").asText()).put("interpretation", "로그 표본");
        return output;
    }

    @Test void originalAlertPrecedesAnalysisAndDeliveryCompletesOnTheCallingThread() throws Exception {
        var order = new ArrayList<String>();
        var nativeClient = mock(DiscordWebhookClient.class);
        doAnswer(invocation -> { order.add("original"); return null; }).when(nativeClient).send(anyString());
        Thread caller = Thread.currentThread();
        var analyzer = new IncidentAnalyzer((uri, headers, body) -> {
            assertSame(caller, Thread.currentThread());
            assertEquals("original", order.get(0));
            order.add("analysis");
            return IncidentFixtures.response(diagnosis(parse(body.path("input").asText())));
        }, "key", "gpt-4.1-mini");
        var trigger = new IncidentAlertTrigger(environment, analyzer, (report, uri) -> {
            assertSame(caller, Thread.currentThread()); order.add("diagnosis");
            assertEquals("LOW", report.at("/diagnosis/confidence/level").asText());
        }, clock);
        var alerts = new ErrorAlertService(nativeClient, environment, trigger);
        try (var scope = IncidentAlertTrigger.openRequest()) {
            alerts.sendOperationalWarning("DB_POOL", "대기", "pending=1");
            assertEquals(java.util.List.of("original", "analysis", "diagnosis"), order);
        }
        assertFalse(IncidentAlertTrigger.isRequestActive());
    }

    @Test void failuresAndMissingKeyKeepNativeAlertsAndDoNotRetryAutomatically() throws Exception {
        for (String failure : new String[]{"401", "429", "timeout", "bad-schema", "missing-key"}) {
            var nativeClient = mock(DiscordWebhookClient.class);
            var calls = new AtomicInteger(); var delivered = new AtomicInteger();
            var analyzer = new IncidentAnalyzer((uri, headers, body) -> {
                calls.incrementAndGet();
                if (failure.equals("bad-schema")) return IncidentFixtures.response(object());
                throw new IOException(failure);
            }, failure.equals("missing-key") ? "" : "key", "gpt-4.1-mini");
            var trigger = new IncidentAlertTrigger(environment, analyzer, (report, uri) -> delivered.incrementAndGet(), clock);
            var alerts = new ErrorAlertService(nativeClient, environment, trigger);
            try (var scope = IncidentAlertTrigger.openRequest()) { alerts.sendOperationalWarning("DB_POOL", "대기", "pending=1"); }
            try (var scope = IncidentAlertTrigger.openRequest()) { alerts.sendOperationalWarning("DB_POOL", "대기", "pending=2"); }
            verify(nativeClient, times(2)).send(anyString());
            assertEquals(failure.equals("missing-key") ? 0 : 1, calls.get());
            assertEquals(0, delivered.get());
        }
    }

    @Test void noRequestDefersAnalysisAndOnlyOneAttemptIsAllowedPerRequest() throws Exception {
        var inputs = new ArrayList<JsonNode>();
        var analyzer = new IncidentAnalyzer((uri, headers, body) -> {
            JsonNode input = parse(body.path("input").asText()); inputs.add(input);
            return IncidentFixtures.response(diagnosis(input));
        }, "key", "gpt-4.1-mini");
        var delivered = new AtomicInteger();
        var trigger = new IncidentAlertTrigger(environment, analyzer, (report, uri) -> delivered.incrementAndGet(), clock);
        trigger.submit("ASYNC_ERROR", "error token=secret-value");
        assertTrue(inputs.isEmpty());
        try (var scope = IncidentAlertTrigger.openRequest()) {
            trigger.flushPending();
            trigger.submit("MEMORY", "usage=800000000");
            assertEquals(1, delivered.get());
        }
        try (var scope = IncidentAlertTrigger.openRequest()) { trigger.flushPending(); }
        assertEquals(2, delivered.get());
        assertFalse(inputs.get(0).toString().contains("secret-value"));
    }

    @Test void deferredWindowRetainsOriginalTriggerAndExcludesFutureLogs() throws Exception {
        var current = new AtomicReference<>(clock.instant());
        Clock advancing = new Clock() {
            public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(java.time.ZoneId zone) { return this; }
            public Instant instant() { return current.get(); }
        };
        var inputs = new ArrayList<JsonNode>();
        var analyzer = new IncidentAnalyzer((uri, headers, body) -> {
            var input = parse(body.path("input").asText()); inputs.add(input);
            return IncidentFixtures.response(diagnosis(input));
        }, "key", "gpt-4.1-mini");
        var trigger = new IncidentAlertTrigger(environment, analyzer, (report, uri) -> {}, advancing);
        trigger.submit("FIRST", "original");
        current.set(current.get().plusSeconds(600));
        trigger.submit("SECOND", "future");
        try (var scope = IncidentAlertTrigger.openRequest()) { trigger.flushPending(); }
        assertEquals("original", inputs.get(0).at("/relatedLogs/0/message").asText());
        assertEquals(1, inputs.get(0).path("relatedLogs").size());
        assertEquals(clock.instant().plusMillis(1).toString(), inputs.get(0).path("windowEnd").asText());
    }
}
