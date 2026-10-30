package com.ybc.ybc.global.monitoring.dto.response;

import com.ybc.ybc.global.monitoring.incident.IncidentTestResult;

public record MonitoringIncidentTestResponse(
        String testId,
        String scenario,
        boolean simulation,
        IncidentTestResult result
) {
}
