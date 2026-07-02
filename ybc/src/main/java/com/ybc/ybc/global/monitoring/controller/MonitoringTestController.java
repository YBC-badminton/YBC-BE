package com.ybc.ybc.global.monitoring.controller;

import com.ybc.ybc.global.monitoring.config.MonitoringProperties;
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
}
