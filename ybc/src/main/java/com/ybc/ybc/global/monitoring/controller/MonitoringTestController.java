package com.ybc.ybc.global.monitoring.controller;

import com.ybc.ybc.global.monitoring.config.MonitoringProperties;
import com.ybc.ybc.global.monitoring.dto.response.MonitoringIncidentTestResponse;
import com.ybc.ybc.global.monitoring.service.ErrorAlertService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@RequiredArgsConstructor
public class MonitoringTestController {
    static final String TEST_ALERT_PATH = "/internal/monitoring/test-alert";
    static final String TEST_INCIDENT_PATH = "/internal/monitoring/test-incident";
    static final String TEST_ALERT_TOKEN_HEADER = "X-Monitoring-Test-Token";
    static final String MANUAL_TRIGGER_SOURCE = "manual-endpoint";

    private final ErrorAlertService errorAlertService;
    private final MonitoringProperties monitoringProperties;

    @PostMapping(TEST_ALERT_PATH)
    public ResponseEntity<Void> sendTestAlert(
            @RequestHeader(name = TEST_ALERT_TOKEN_HEADER, required = false) String token,
            HttpServletRequest request
    ) {
        MonitoringProperties.TestEndpoint testEndpoint = monitoringProperties.testEndpoint();
        if (testEndpoint == null || !testEndpoint.enabled()) {
            return ResponseEntity.notFound().build();
        }

        if (!StringUtils.hasText(testEndpoint.token()) || !Objects.equals(testEndpoint.token(), token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        errorAlertService.sendManualTestAlert(request, MANUAL_TRIGGER_SOURCE);
        return ResponseEntity.accepted().build();
    }

    @PostMapping(TEST_INCIDENT_PATH)
    public ResponseEntity<MonitoringIncidentTestResponse> sendTestIncident(
            @RequestHeader(name = TEST_ALERT_TOKEN_HEADER, required = false) String token
    ) {
        MonitoringProperties.TestEndpoint endpoint = monitoringProperties.testEndpoint();
        if (endpoint == null || !endpoint.enabled()) return ResponseEntity.notFound().build();
        if (!StringUtils.hasText(endpoint.token()) || !Objects.equals(endpoint.token(), token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        var response = errorAlertService.sendManualIncidentTestAlert();
        return switch (response.result().status()) {
            case DELIVERED -> ResponseEntity.ok(response);
            case COOLDOWN -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", Long.toString(response.result().retryAfterSeconds())).body(response);
            case DISABLED, NOT_CONFIGURED, BUSY -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
            case FAILED -> ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(response);
        };
    }
}
