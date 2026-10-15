package com.ybc.ybc.global.monitoring.service;

import com.ybc.ybc.global.monitoring.config.MonitoringProperties;
import com.ybc.ybc.global.monitoring.config.OperationalMonitoringProperties;
import com.ybc.ybc.global.monitoring.discord.DiscordWebhookClient;
import com.ybc.ybc.global.monitoring.incident.IncidentAlertTrigger;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Opportunistic checks before serving an HTTP request; no idle polling or background job. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class RequestMonitoringFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestMonitoringFilter.class);
    private final MemoryUsageMonitor memory;
    private final DatabasePoolMonitor pool;
    private final HttpRequestMonitor http;
    private final DatabaseQuotaMonitor quota;
    private final DiscordWebhookClient discord;
    private final IncidentAlertTrigger diagnosis;
    private final Cadence memoryCadence, operationalCadence, quotaCadence;

    public RequestMonitoringFilter(MemoryUsageMonitor memory, DatabasePoolMonitor pool, HttpRequestMonitor http,
                                   DatabaseQuotaMonitor quota, DiscordWebhookClient discord, IncidentAlertTrigger diagnosis,
                                   OperationalMonitoringProperties operationalSettings,
                                   MonitoringProperties settings, org.springframework.core.env.Environment environment) {
        this.memory = memory; this.pool = pool; this.http = http; this.quota = quota; this.discord = discord; this.diagnosis = diagnosis;
        long now = System.nanoTime();
        memoryCadence = new Cadence(environment.getProperty("app.monitoring.memory.poll-interval-ms", Long.class, 30000L),
                environment.getProperty("app.monitoring.memory.initial-delay-ms", Long.class, 60000L), now);
        operationalCadence = new Cadence(operationalSettings.pollIntervalMs(), operationalSettings.initialDelayMs(), now);
        var db = settings.supabase();
        quotaCadence = new Cadence(db == null ? 1800000 : db.pollIntervalMs(), db == null ? 60000 : db.initialDelayMs(), now);
    }

    @Override protected boolean shouldNotFilterAsyncDispatch() { return false; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.equals("/actuator") || path.startsWith("/actuator/");
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        AtomicLong cost = (AtomicLong) request.getAttribute(IncidentAlertTrigger.COST_ATTRIBUTE);
        AtomicBoolean attempted = (AtomicBoolean) request.getAttribute(IncidentAlertTrigger.ATTEMPT_ATTRIBUTE);
        if (cost == null) {
            cost = new AtomicLong(); attempted = new AtomicBoolean();
            request.setAttribute(IncidentAlertTrigger.COST_ATTRIBUTE, cost);
            request.setAttribute(IncidentAlertTrigger.ATTEMPT_ATTRIBUTE, attempted);
        }
        try (var scope = IncidentAlertTrigger.openRequest(cost, attempted)) {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            if (request.getDispatcherType() == DispatcherType.REQUEST && !path.equals("/internal") && !path.startsWith("/internal/")) {
                IncidentAlertTrigger.runMonitoring(() -> {
                    safely(discord::flushPending);
                    long tick = System.nanoTime();
                    if (memoryCadence.claim(tick)) safely(memory::checkMemory);
                    if (operationalCadence.claim(tick)) { safely(pool::checkPool); safely(http::checkRequests); }
                    if (quotaCadence.claim(tick)) safely(quota::checkDatabaseSize);
                    safely(diagnosis::flushPending);
                });
            }
            chain.doFilter(request, response);
        }
    }

    private static void safely(Runnable check) {
        try { check.run(); }
        catch (RuntimeException failure) { log.warn("Request monitoring unavailable; business request continues."); }
    }

    static final class Cadence {
        private final long period;
        private final AtomicLong next;
        Cadence(long periodMs, long initialMs, long now) {
            if (periodMs < 1 || periodMs > 86_400_000 || initialMs < 0 || initialMs > 86_400_000) {
                throw new IllegalArgumentException("감시 간격/초기 지연은 1일 이내여야 합니다.");
            }
            period = java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(periodMs);
            next = new AtomicLong(now + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(initialMs));
        }
        boolean claim(long now) {
            long due = next.get();
            return now - due >= 0 && next.compareAndSet(due, now + period);
        }
    }
}
