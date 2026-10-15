package com.ybc.ybc.global.monitoring.service;

import com.ybc.ybc.global.monitoring.config.MonitoringProperties;
import com.ybc.ybc.global.monitoring.config.OperationalMonitoringProperties;
import com.ybc.ybc.global.monitoring.discord.DiscordWebhookClient;
import com.ybc.ybc.global.monitoring.incident.IncidentAlertTrigger;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequestMonitoringFilterTest {
    private final MemoryUsageMonitor memory = mock(MemoryUsageMonitor.class);
    private final DatabasePoolMonitor pool = mock(DatabasePoolMonitor.class);
    private final HttpRequestMonitor http = mock(HttpRequestMonitor.class);
    private final DatabaseQuotaMonitor quota = mock(DatabaseQuotaMonitor.class);
    private final DiscordWebhookClient discord = mock(DiscordWebhookClient.class);
    private final IncidentAlertTrigger diagnosis = mock(IncidentAlertTrigger.class);
    private final RequestMonitoringFilter filter = new RequestMonitoringFilter(memory, pool, http, quota, discord, diagnosis,
            new OperationalMonitoringProperties(true, 10000, 0, 1, 60, 300, 20, 1000, 800, 60, 3),
            new MonitoringProperties(null, new MonitoringProperties.Supabase(false, 1800000, 0, 500, 70, 85, 95), null),
            new MockEnvironment().withProperty("app.monitoring.memory.initial-delay-ms", "0"));

    @Test void checksRunDuringRequestOnlyAndCadencePreventsRepeatedChecks() throws Exception {
        verifyNoInteractions(memory, pool, http, quota, discord, diagnosis);
        var request = new MockHttpServletRequest("GET", "/members");
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            assertTrue(IncidentAlertTrigger.isRequestActive());
            verify(memory).checkMemory(); verify(pool).checkPool(); verify(http).checkRequests(); verify(quota).checkDatabaseSize();
        });
        assertFalse(IncidentAlertTrigger.isRequestActive());
        assertTrue(((AtomicLong) request.getAttribute(IncidentAlertTrigger.COST_ATTRIBUTE)).get() > 0);
        filter.doFilter(new MockHttpServletRequest("GET", "/members"), new MockHttpServletResponse(), (req, res) -> {});
        verify(memory, times(1)).checkMemory(); verify(pool, times(1)).checkPool();
        verify(discord, times(2)).flushPending(); verify(diagnosis, times(2)).flushPending();
    }

    @Test void failedCheckDoesNotStopOtherChecksOrBusinessAndScopeIsAlwaysClosed() throws Exception {
        doThrow(new IllegalStateException("unavailable")).when(memory).checkMemory();
        assertThrows(ServletException.class, () -> filter.doFilter(new MockHttpServletRequest("GET", "/members"),
                new MockHttpServletResponse(), (req, res) -> {
                    verify(pool).checkPool(); assertTrue(IncidentAlertTrigger.isRequestActive());
                    throw new ServletException("business error");
                }));
        assertFalse(IncidentAlertTrigger.isRequestActive());
    }

    @Test void healthProbesSkipWorkAndManualEndpointHasScopeWithoutAutomaticChecks() throws Exception {
        filter.doFilter(new MockHttpServletRequest("GET", "/actuator/health"), new MockHttpServletResponse(),
                (req, res) -> assertFalse(IncidentAlertTrigger.isRequestActive()));
        filter.doFilter(new MockHttpServletRequest("POST", "/internal/monitoring/test-alert"), new MockHttpServletResponse(),
                (req, res) -> assertTrue(IncidentAlertTrigger.isRequestActive()));
        verifyNoInteractions(memory, pool, http, quota, discord, diagnosis);
    }

    @Test void asyncRedispatchReusesRequestBudgetAndDoesNotRunChecksAgain() throws Exception {
        var request = new MockHttpServletRequest("GET", "/members");
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});
        var cost = request.getAttribute(IncidentAlertTrigger.COST_ATTRIBUTE);
        var attempted = request.getAttribute(IncidentAlertTrigger.ATTEMPT_ATTRIBUTE);
        request.setDispatcherType(DispatcherType.ASYNC);
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            assertTrue(IncidentAlertTrigger.isRequestActive());
            assertSame(cost, req.getAttribute(IncidentAlertTrigger.COST_ATTRIBUTE));
            assertSame(attempted, req.getAttribute(IncidentAlertTrigger.ATTEMPT_ATTRIBUTE));
        });
        verify(discord, times(1)).flushPending(); verify(diagnosis, times(1)).flushPending();
        assertFalse(IncidentAlertTrigger.isRequestActive());
    }

    @Test void longIdleGapClaimsOnlyOneCheckRatherThanCatchingUp() {
        var cadence = new RequestMonitoringFilter.Cadence(10000, 0, 0);
        assertTrue(cadence.claim(0)); assertFalse(cadence.claim(0));
        assertTrue(cadence.claim(1_000_000_000_000L)); assertFalse(cadence.claim(1_000_000_000_000L));
    }
}
