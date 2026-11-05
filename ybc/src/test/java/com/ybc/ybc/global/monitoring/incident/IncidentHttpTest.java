package com.ybc.ybc.global.monitoring.incident;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.ybc.ybc.global.monitoring.incident.IncidentJson.*;
import static org.junit.jupiter.api.Assertions.*;

class IncidentHttpTest {
    @ParameterizedTest
    @CsvSource({
            "credit_balance_exhausted,insufficient_quota,CREDIT_BALANCE_EXHAUSTED",
            "insufficient_quota,insufficient_quota,INSUFFICIENT_QUOTA",
            "project_spend_limit_exceeded,insufficient_quota,INSUFFICIENT_QUOTA",
            ",insufficient_quota,INSUFFICIENT_QUOTA",
            "rate_limit_exceeded,rate_limit_error,",
            "unknown,unknown,"
    })
    void transportClassifiesQuotaWithoutRetainingProviderMessage(String code, String type, IncidentHttp.QuotaFailure expected) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var calls = new AtomicInteger();
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            byte[] body = object().set("error", object().put("code", code).put("type", type)
                    .put("message", "credit_balance_exhausted sk-test-secret sensitive-provider-body"))
                    .toString().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(429, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var failure = assertThrows(IncidentHttp.Failure.class, () -> IncidentHttp.create().post(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()), Map.of(), object()));
            assertEquals(429, failure.statusCode());
            assertEquals(expected, failure.quotaFailure());
            assertFalse(failure.toString().contains("sk-test-secret"));
            assertFalse(failure.toString().contains("sensitive-provider-body"));
            assertNull(failure.getCause());
            assertEquals(1, calls.get());
        } finally { server.stop(0); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-json sk-test-secret", "null", "[]", "{\"error\":{\"code\":\"sk-test-secret\"}}"})
    void malformedAndUnknownRateLimitResponsesAreNotReportedAsCreditExhaustion(String body) {
        var failure = IncidentHttp.Failure.fromResponse(429, body.getBytes(StandardCharsets.UTF_8));
        assertEquals(429, failure.statusCode());
        assertNull(failure.quotaFailure());
        assertFalse(failure.toString().contains("sk-test-secret"));
    }

    @Test
    void quotaLookingBodyOnAnotherStatusIsNotClassifiedAsQuota() {
        byte[] body = "{\"error\":{\"code\":\"credit_balance_exhausted\"}}".getBytes(StandardCharsets.UTF_8);
        assertNull(IncidentHttp.Failure.fromResponse(500, body).quotaFailure());
    }

    @Test
    void transportBoundsResponseBytesDuringReception() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] large = "x".repeat(MAX_FILE_BYTES + 1).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, large.length);
            try (var stream = exchange.getResponseBody()) { stream.write(large); }
            exchange.close();
        });
        server.start();
        try {
            assertThrows(IOException.class, () -> IncidentHttp.create().post(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()), Map.of(), object()));
        } finally { server.stop(0); }
    }

    @Test
    void realTransportPostsJsonButDoesNotRetryRateLimitsOrExposeErrorBodies() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            assertEquals("POST", exchange.getRequestMethod());
            assertEquals("application/json", exchange.getRequestHeaders().getFirst("Content-Type"));
            assertEquals("Bearer test", exchange.getRequestHeaders().getFirst("Authorization"));
            assertEquals("test", parse(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).path("name").asText());
            byte[] error = "sensitive-provider-body".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(429, error.length);
            exchange.getResponseBody().write(error);
            exchange.close();
        });
        server.start();
        try {
            IOException error = assertThrows(IOException.class, () -> IncidentHttp.create().post(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()), Map.of("Authorization", "Bearer test"), object().put("name", "test")));
            assertTrue(error.getMessage().contains("429"));
            assertEquals(429, ((IncidentHttp.Failure) error).statusCode());
            assertFalse(error.getMessage().contains("sensitive-provider-body"));
            assertEquals(1, calls.get());
        } finally { server.stop(0); }
    }

    @Test
    void transportNeverFollowsRedirectWithCredentials() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger redirected = new AtomicInteger();
        server.createContext("/start", exchange -> {
            exchange.getResponseHeaders().add("Location", "/target");
            exchange.sendResponseHeaders(307, -1); exchange.close();
        });
        server.createContext("/target", exchange -> {
            redirected.incrementAndGet(); exchange.sendResponseHeaders(204, -1); exchange.close();
        });
        server.start();
        try {
            assertThrows(IOException.class, () -> IncidentHttp.create().post(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/start"),
                    Map.of("Authorization", "Bearer test"), object()));
            assertEquals(0, redirected.get());
        } finally { server.stop(0); }
    }
}
