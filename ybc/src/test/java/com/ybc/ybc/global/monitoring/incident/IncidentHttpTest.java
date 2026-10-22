package com.ybc.ybc.global.monitoring.incident;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.ybc.ybc.global.monitoring.incident.IncidentJson.*;
import static org.junit.jupiter.api.Assertions.*;

class IncidentHttpTest {
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
