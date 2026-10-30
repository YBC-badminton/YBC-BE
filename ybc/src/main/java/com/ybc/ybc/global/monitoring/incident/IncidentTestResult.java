package com.ybc.ybc.global.monitoring.incident;

/** Safe delivery outcome: never includes provider responses, credentials, or exception messages. */
public record IncidentTestResult(Status status, String stage, String reason, long retryAfterSeconds) {
    public enum Status { DELIVERED, DISABLED, NOT_CONFIGURED, COOLDOWN, BUSY, FAILED }
}
