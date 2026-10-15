package com.ybc.ybc.global.monitoring.service;

import com.ybc.ybc.support.TestMailConfig;
import com.ybc.ybc.global.monitoring.incident.IncidentAlertTrigger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.monitoring.memory.enabled=false", "app.monitoring.operational.initial-delay-ms=600000"})
@Import({TestMailConfig.class, HttpObservationIntegrationTest.ProbeConfiguration.class})
class HttpObservationIntegrationTest {
    @Autowired HttpRequestWindow window;
    @Autowired Environment environment;
    @Autowired ProbeController probe;

    @Test void springRegistersObserverAndRealHttpSecurityResponsesAreCounted() throws Exception {
        String base = "http://localhost:" + environment.getProperty("local.server.port");
        var client = HttpClient.newHttpClient();
        client.send(HttpRequest.newBuilder(URI.create(base + "/actuator/health")).GET().build(), HttpResponse.BodyHandlers.discarding());
        assertEquals(0, window.snapshot(Instant.now().plusSeconds(1)).requests());
        var response = client.send(HttpRequest.newBuilder(URI.create(base + "/members")).GET().build(), HttpResponse.BodyHandlers.discarding());
        assertEquals(401, response.statusCode());
        assertEquals(1, window.snapshot(Instant.now().plusSeconds(1)).requests());
        assertEquals(0, window.snapshot(Instant.now().plusSeconds(1)).errors5xx());
        assertEquals(500, client.send(HttpRequest.newBuilder(URI.create(base + "/monitoring-probe/failure")).GET().build(),
                HttpResponse.BodyHandlers.discarding()).statusCode());
        assertEquals(503, client.send(HttpRequest.newBuilder(URI.create(base + "/monitoring-probe/async")).GET().build(),
                HttpResponse.BodyHandlers.discarding()).statusCode());
        for (int i = 0; i < 100 && window.snapshot(Instant.now().plusSeconds(1)).requests() < 3; i++) Thread.sleep(10);
        assertEquals(3, window.snapshot(Instant.now().plusSeconds(1)).requests());
        assertEquals(2, window.snapshot(Instant.now().plusSeconds(1)).errors5xx());

        var waitingResponse = client.sendAsync(HttpRequest.newBuilder(URI.create(base + "/monitoring-probe/blocking")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        try {
            assertTrue(probe.entered.await(5, TimeUnit.SECONDS));
            assertFalse(waitingResponse.isDone(), "response must wait for request-bound monitoring work");
        } finally { probe.release.countDown(); }
        assertEquals(200, waitingResponse.get(5, TimeUnit.SECONDS).statusCode());
        assertEquals(4, window.snapshot(Instant.now().plusSeconds(1)).requests());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfiguration {
        @Bean ProbeController probeController() { return new ProbeController(); }
        @Bean @Order(-1)
        SecurityFilterChain probeSecurity(HttpSecurity http) throws Exception {
            return http.securityMatcher("/monitoring-probe/**").authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
        }
    }

    @RestController
    static class ProbeController {
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        @GetMapping("/monitoring-probe/blocking") ResponseEntity<String> blocking() {
            if (!IncidentAlertTrigger.isRequestActive()) return ResponseEntity.status(500).body("missing request scope");
            IncidentAlertTrigger.runMonitoring(() -> {
                entered.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            });
            return ResponseEntity.ok("completed");
        }
        @GetMapping("/monitoring-probe/failure") ResponseEntity<String> failure() { return ResponseEntity.status(500).body("synthetic"); }
        @GetMapping("/monitoring-probe/async") Callable<ResponseEntity<String>> async() {
            return () -> ResponseEntity.status(503).body("synthetic");
        }
    }
}
