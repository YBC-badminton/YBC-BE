package com.ybc.ybc.global.monitoring.service;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import com.ybc.ybc.global.monitoring.incident.IncidentAlertTrigger;

/** Uses Spring's existing servlet observation, including its async request completion handling. */
@Component
public class HttpRequestObservationHandler implements ObservationHandler<ServerRequestObservationContext> {
    private final HttpRequestWindow window;
    public HttpRequestObservationHandler(HttpRequestWindow window) { this.window = window; }

    @Override public boolean supportsContext(Observation.Context context) { return context instanceof ServerRequestObservationContext; }

    @Override public void onStart(ServerRequestObservationContext context) {
        if (context.getCarrier() == null) return;
        String path = context.getCarrier().getRequestURI().substring(context.getCarrier().getContextPath().length());
        if (path.equals("/actuator") || path.startsWith("/actuator/") || path.equals("/internal") || path.startsWith("/internal/")) return;
        context.put(Timing.class, new Timing(System.nanoTime()));
    }

    @Override public void onStop(ServerRequestObservationContext context) {
        Object entry = context.remove(Timing.class);
        if (!(entry instanceof Timing timing) || context.getResponse() == null) return;
        Object cost = context.getCarrier().getAttribute(IncidentAlertTrigger.COST_ATTRIBUTE);
        long monitoringNanos = cost instanceof AtomicLong value ? value.get() : 0;
        window.record(context.getResponse().getStatus(), Math.max(0, System.nanoTime() - timing.startedNanos() - monitoringNanos), Instant.now());
    }

    private record Timing(long startedNanos) { }
}
