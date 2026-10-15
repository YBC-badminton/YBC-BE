package com.ybc.ybc.global.monitoring.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;
import com.ybc.ybc.global.monitoring.incident.IncidentAlertTrigger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class HttpRequestObservationHandlerTest {
    @Test void excludesMonitoringWorkFromOwnLatencySample() {
        var window = new HttpRequestWindow(OperationalFixtures.settings());
        var handler = new HttpRequestObservationHandler(window);
        var request = new MockHttpServletRequest("GET", "/api/members");
        request.setAttribute(IncidentAlertTrigger.COST_ATTRIBUTE, new AtomicLong(Long.MAX_VALUE));
        var context = new ServerRequestObservationContext(request, new MockHttpServletResponse());
        handler.onStart(context); handler.onStop(context);
        var sample = window.snapshot(Instant.now().plusSeconds(1));
        assertEquals(1, sample.requests());
        assertEquals(0, sample.p95LowerMs());
    }
    @Test void recordsFinalResponseOnceAndExcludesOperationalPathsWithinContextPath() {
        var window = new HttpRequestWindow(OperationalFixtures.settings());
        var handler = new HttpRequestObservationHandler(window);
        for (String path : new String[]{"/app/actuator/health", "/app/internal/ai-incidents", "/app/api/members"}) {
            var request = new MockHttpServletRequest("GET", path); request.setContextPath("/app");
            var response = new MockHttpServletResponse();
            var context = new ServerRequestObservationContext(request, response);
            handler.onStart(context);
            response.setStatus(503);
            handler.onStop(context);
            handler.onStop(context);
        }
        var sample = window.snapshot(Instant.now().plusSeconds(1));
        assertEquals(1, sample.requests());
        assertEquals(1, sample.errors5xx());
    }
}
