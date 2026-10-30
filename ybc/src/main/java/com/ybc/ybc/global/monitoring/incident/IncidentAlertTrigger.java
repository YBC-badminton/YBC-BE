package com.ybc.ybc.global.monitoring.incident;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ybc.ybc.global.monitoring.service.ErrorAlertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static com.ybc.ybc.global.monitoring.incident.IncidentJson.*;
import static com.ybc.ybc.global.monitoring.incident.IncidentTestResult.Status.*;

/** No scheduler, executor, or Redis queue: analysis runs only while an HTTP request is active. */
@Component
public class IncidentAlertTrigger {
    public static final String COST_ATTRIBUTE = IncidentAlertTrigger.class.getName() + ".cost";
    public static final String ATTEMPT_ATTRIBUTE = IncidentAlertTrigger.class.getName() + ".attempt";
    private static final Logger log = LoggerFactory.getLogger(IncidentAlertTrigger.class);
    private static final ThreadLocal<RequestScope> ACTIVE = new ThreadLocal<>();
    private final boolean enabled;
    private final String service, revision, webhook;
    private final IncidentAnalyzer analyzer;
    private final Delivery delivery;
    private final Clock clock;
    private final Semaphore analysisSlot = new Semaphore(1);
    private final ArrayDeque<Alert> recent = new ArrayDeque<>();
    private final LinkedHashMap<String, Alert> pending = new LinkedHashMap<>();
    private final LinkedHashMap<String, Instant> attempted = new LinkedHashMap<>();

    @Autowired
    public IncidentAlertTrigger(Environment environment, ObjectProvider<ErrorAlertService> alerts) throws IOException {
        this(environment, analyzer(environment), (report, target) -> alerts.getObject().sendIncidentDiagnosisAlert(report, target), Clock.systemUTC());
    }

    IncidentAlertTrigger(Environment environment, IncidentAnalyzer analyzer, Delivery delivery, Clock clock) {
        enabled = environment.getProperty("app.monitoring.incident.enabled", Boolean.class, false);
        service = environment.getProperty("spring.application.name", "ybc");
        revision = environment.getProperty("K_REVISION", "UNKNOWN");
        webhook = environment.getProperty("app.monitoring.incident.discord-webhook-url",
                environment.getProperty("app.monitoring.discord.webhook-url", ""));
        this.analyzer = analyzer; this.delivery = delivery; this.clock = clock;
        log.info("Incident analysis configuration: enabled={}, apiKeyConfigured={}, webhookConfigured={}, mode=REQUEST_BOUND",
                enabled, analyzer != null && analyzer.isConfigured(), webhook != null && !webhook.isBlank());
    }

    private static IncidentAnalyzer analyzer(Environment environment) throws IOException {
        String key = environment.getProperty("app.monitoring.incident.api-key", "");
        if (!environment.getProperty("app.monitoring.incident.enabled", Boolean.class, false) || !key.matches("[\\x21-\\x7E]+")) return null;
        try {
            return new IncidentAnalyzer(IncidentHttp.create(), key, environment.getProperty("app.monitoring.incident.model", "gpt-4.1-mini"));
        } catch (IllegalArgumentException invalidConfiguration) {
            log.warn("Diagnosis configuration unavailable; original monitoring alerts remain enabled.");
            return null;
        }
    }

    public void submit(String name, String message) {
        if (!enabled || analyzer == null || !analyzer.isConfigured()) return;
        Alert alert = new Alert(name, IncidentInput.boundedLog(message), clock.instant());
        synchronized (this) {
            recent.removeIf(old -> old.at().isBefore(alert.at().minusSeconds(300)));
            recent.addLast(alert);
            while (recent.size() > 100) recent.removeFirst();
        }
        runMonitoring(() -> processOrDefer(alert));
    }

    public void flushPending() {
        if (!isRequestActive()) return;
        Alert alert;
        synchronized (this) {
            if (pending.isEmpty()) return;
            alert = pending.remove(pending.keySet().iterator().next());
        }
        runMonitoring(() -> processOrDefer(alert));
    }

    /** Uses the production analyzer and delivery boundary, but never mixes test evidence into live alerts. */
    public IncidentTestResult testAdminAccessDenied(String testId, String message) {
        if (!enabled) return new IncidentTestResult(DISABLED, "CONFIGURATION", "INCIDENT_ANALYSIS_DISABLED", 0);
        if (analyzer == null || !analyzer.isConfigured()) {
            return new IncidentTestResult(NOT_CONFIGURED, "CONFIGURATION", "OPENAI_NOT_CONFIGURED", 0);
        }
        if (!isRequestActive()) return new IncidentTestResult(FAILED, "REQUEST_SCOPE", "NO_ACTIVE_REQUEST", 0);
        var result = new AtomicReference<IncidentTestResult>();
        var alert = new Alert("MANUAL_TEST_ADMIN_ACCESS_DENIED", IncidentInput.boundedLog(message), clock.instant());
        runMonitoring(() -> result.set(processOrDefer(alert, testId)));
        return result.get();
    }

    private IncidentTestResult processOrDefer(Alert alert) { return processOrDefer(alert, null); }

    private IncidentTestResult processOrDefer(Alert alert, String testId) {
        boolean simulation = testId != null;
        Instant now = clock.instant();
        synchronized (this) {
            Instant previous = attempted.get(alert.name());
            if (previous != null && !now.isBefore(previous) && now.isBefore(previous.plusSeconds(300))) {
                long remainingMs = Duration.between(now, previous.plusSeconds(300)).toMillis();
                return new IncidentTestResult(COOLDOWN, "RATE_LIMIT", "SAME_ALERT_COOLDOWN", Math.max(1, (remainingMs + 999) / 1000));
            }
        }
        RequestScope scope = ACTIVE.get();
        if (scope == null || !scope.attempt.compareAndSet(false, true) || !analysisSlot.tryAcquire()) {
            if (!simulation) {
                synchronized (this) {
                    if (pending.size() < 20 || pending.containsKey(alert.name())) pending.putIfAbsent(alert.name(), alert);
                    else log.warn("Deferred diagnosis buffer full; original alert is handled separately.");
                }
            }
            return new IncidentTestResult(BUSY, "ADMISSION", "ANALYSIS_BUSY_OR_REQUEST_ALREADY_ATTEMPTED", 0);
        }
        String stage = "COLLECT";
        try {
            var event = object().put("jobId", hash(object().put("name", alert.name()).put("at", alert.at().toString())))
                    .put("job", service).put("revision", revision).put("alertName", alert.name())
                    .put("startsAt", alert.at().toString()).put("receivedAt", alert.at().plusMillis(1).toString())
                    .put("source", "APPLICATION").put("triggerMessage", alert.message());
            var logs = event.putArray("relatedLogs");
            synchronized (this) {
                attempted.put(alert.name(), now);
                while (attempted.size() > 64) attempted.remove(attempted.keySet().iterator().next());
                pending.remove(alert.name());
                for (Alert entry : recent) if (!simulation && !entry.at().isBefore(alert.at().plusMillis(1).minusSeconds(300)) && !entry.at().isAfter(alert.at())) {
                    logs.add(object().put("timestamp", entry.at().toString()).put("message", entry.message()));
                }
            }
            ObjectNode raw = new ApplicationIncidentCollector().collect(event);
            if (simulation) {
                raw.put("incidentId", "test-" + testId);
                raw.put("sourceType", "SIMULATION");
                raw.put("metricScope", "연결 점검용 합성 접근 거부 로그 1건. 실제 사용자 접근이나 운영 장애를 나타내지 않음.");
                raw.putArray("collectionNotes").add("가상 회원·가상 경로의 403 사건. 실제 접근 제어/침입 탐지 동작은 검증하지 않음.");
            }
            ObjectNode input = IncidentInput.prepare(raw, "multi", Set.of());
            stage = "DISCORD_CONFIGURATION";
            URI target = IncidentDiscord.webhook(webhook);
            stage = "OPENAI_ANALYSIS";
            long tick = System.nanoTime();
            var result = analyzer.analyze(input);
            stage = "BUILD_REPORT";
            var report = IncidentReport.create(raw, input, "multi", Set.of(), result, now,
                    (System.nanoTime() - tick) / 1_000_000, analyzer.promptVersion());
            stage = "DISCORD_DELIVERY";
            delivery.send(report, target);
            log.info("Incident diagnosis delivered: analysisDurationMs={}, logCount={}",
                    report.path("analysisDurationMs").asLong(), input.path("relatedLogs").size());
            return new IncidentTestResult(DELIVERED, "DISCORD_DELIVERY", "DISCORD_MESSAGE_CONFIRMED", 0);
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("Diagnosis unavailable: stage={}, reason={}, exceptionType={}; original monitoring alert remains. No automatic retry.",
                    stage, safeReason(failure), failure.getClass().getSimpleName());
            return new IncidentTestResult(FAILED, stage, safeReason(failure), 0);
        } finally { analysisSlot.release(); }
    }

    private static String safeReason(Exception failure) {
        if (failure instanceof IncidentJson.ValidationFailure) return failure.getMessage();
        if (failure instanceof IncidentHttp.Failure http && http.statusCode() > 0) return "HTTP_STATUS_" + http.statusCode();
        if (failure instanceof IOException) return "IO_OR_TIMEOUT";
        if (failure instanceof InterruptedException) return "INTERRUPTED";
        // Unknown exception messages may contain credentials, operational logs, URLs, or provider bodies.
        return "UNEXPECTED_FAILURE";
    }

    public static boolean isRequestActive() { return ACTIVE.get() != null; }
    public static String maskMessage(String message) { return IncidentInput.boundedLog(message); }

    public static RequestScope openRequest(AtomicLong cost, AtomicBoolean attempt) { return new RequestScope(cost, attempt); }
    public static RequestScope openRequest() { return openRequest(new AtomicLong(), new AtomicBoolean()); }

    public static void runMonitoring(Runnable work) {
        RequestScope scope = ACTIVE.get();
        if (scope == null) { work.run(); return; }
        boolean outer = scope.depth++ == 0;
        long started = System.nanoTime();
        try { work.run(); }
        finally { if (--scope.depth == 0 && outer) scope.cost.addAndGet(System.nanoTime() - started); }
    }

    public static final class RequestScope implements AutoCloseable {
        private final AtomicLong cost;
        private final AtomicBoolean attempt;
        private final RequestScope previous;
        private int depth;
        private RequestScope(AtomicLong cost, AtomicBoolean attempt) {
            this.cost = cost; this.attempt = attempt; previous = ACTIVE.get(); ACTIVE.set(this);
        }
        @Override public void close() { if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous); }
    }

    private record Alert(String name, String message, Instant at) { }
    @FunctionalInterface interface Delivery { void send(JsonNode report, URI target) throws Exception; }
}
