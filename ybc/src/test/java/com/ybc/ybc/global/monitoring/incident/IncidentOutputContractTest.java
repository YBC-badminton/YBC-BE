package com.ybc.ybc.global.monitoring.incident;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.ybc.ybc.global.monitoring.incident.IncidentFixtures.*;
import static com.ybc.ybc.global.monitoring.incident.IncidentJson.*;
import static org.junit.jupiter.api.Assertions.*;

class IncidentOutputContractTest {
    static ObjectNode scope(String unit, String aggregation, String target, String revision) {
        return object().put("unit", unit).put("aggregation", aggregation).put("target", target).put("revision", revision);
    }

    private ObjectNode baselineInput() {
        ObjectNode input = input().put("baselineP95Latency", 0.4)
                .put("baselineWindowStart", "2026-08-01T09:55:00Z").put("baselineWindowEnd", "2026-08-01T10:00:00Z");
        ObjectNode scopes = input.putObject("metricScopes");
        scopes.set("p95Latency", scope("seconds", "HISTOGRAM_P95_5M", "api/instance-1/http", "rev-1"));
        scopes.set("baselineP95Latency", scopes.path("p95Latency").deepCopy());
        return input;
    }

    @Test void baselineIsComparedOnlyWhenUnitsAggregationTargetAndRevisionMatch() {
        assertTrue(IncidentInput.prepare(baselineInput(), "multi", Set.of()).hasNonNull("baselineP95Latency"));
        for (String dimension : Set.of("unit", "aggregation", "target", "revision")) {
            ObjectNode raw = baselineInput();
            ((ObjectNode) raw.at("/metricScopes/baselineP95Latency")).put(dimension, "different");
            ObjectNode prepared = IncidentInput.prepare(raw, "multi", Set.of());
            assertFalse(prepared.has("baselineP95Latency"));
            assertEquals("INCOMPARABLE", prepared.at("/metricComparisons/baselineP95Latency/status").asText());
            assertEquals(0.4, raw.path("baselineP95Latency").asDouble()); // Keep raw observations for human review.
        }
        ObjectNode unknown = baselineInput();
        ((ObjectNode) unknown.at("/metricScopes/p95Latency")).put("revision", "UNKNOWN");
        assertFalse(IncidentInput.prepare(unknown, "multi", Set.of()).has("baselineP95Latency"));
        ObjectNode absent = baselineInput(); absent.remove("metricScopes");
        assertEquals("UNVERIFIED", IncidentInput.prepare(absent, "multi", Set.of())
                .at("/metricComparisons/baselineP95Latency/status").asText());
    }

    @Test void mismatchedResourceScopesAndMaxVersusMinAreNotComparable() {
        ObjectNode raw = input();
        ObjectNode scopes = raw.putObject("metricScopes");
        scopes.set("memoryUsageMb", scope("MiB", "WINDOW_MAX", "all-instances", "rev-1"));
        scopes.set("memoryLimitMb", scope("MiB", "WINDOW_MAX", "instance-1", "rev-1"));
        scopes.set("dbActiveConnections", scope("connections", "WINDOW_MAX", "database/server", "rev-1"));
        scopes.set("dbMaxConnections", scope("connections", "WINDOW_MAX", "app/hikari/pool-1", "rev-1"));
        ObjectNode prepared = IncidentInput.prepare(raw, "multi", Set.of());
        assertEquals("INCOMPARABLE", prepared.at("/metricComparisons/memoryLimitMb/status").asText());
        assertEquals("INCOMPARABLE", prepared.at("/metricComparisons/dbMaxConnections/status").asText());
        ((ObjectNode) scopes.path("dbActiveConnections")).put("target", "app/hikari/pool-1");
        ((ObjectNode) scopes.path("dbMaxConnections")).put("aggregation", "WINDOW_MIN");
        assertEquals("INCOMPARABLE", IncidentInput.prepare(raw, "multi", Set.of())
                .at("/metricComparisons/dbMaxConnections/status").asText());
    }

    @Test void diagnosisCannotCiteUnverifiedBaselineEvenIfRawObservationIsPresent() throws Exception {
        var analyzer = new IncidentAnalyzer(null, null, "gpt-4.1-mini");
        ObjectNode raw = baselineInput(), d = diagnosis();
        d.putArray("evidence").addObject().put("field", "/baselineP95Latency").put("value", "0.4").put("interpretation", "직전 P95");
        ((ObjectNode) d.at("/causeCandidates/0")).putArray("evidenceFields").add("/baselineP95Latency");
        assertDoesNotThrow(() -> analyzer.validateDiagnosis(d, raw));
        ((ObjectNode) raw.at("/metricScopes/baselineP95Latency")).put("revision", "rev-2");
        assertThrows(IllegalArgumentException.class, () -> analyzer.validateDiagnosis(d, raw));
    }

    @Test void numericEvidenceUsesParsedWireValueAndLogEvidenceMustContainWholeMaskedMessage() throws Exception {
        var analyzer = new IncidentAnalyzer(null, null, "gpt-4.1-mini");
        ObjectNode raw = input(); raw.set("memoryUsageMb", parse("1.00"));
        ((ObjectNode) raw.at("/relatedLogs/0")).put("message", "Connection timeout; password=secret\ncheck pool");
        ObjectNode prepared = IncidentInput.prepare(raw, "multi", Set.of());
        String wireValue = parse(prepared.toString()).path("memoryUsageMb").asText();
        assertEquals("1.0", wireValue);
        assertEquals(wireValue, prepared.at("/metricEvidenceValues/memoryUsageMb").asText());
        ObjectNode d = diagnosis();
        ((ObjectNode) d.at("/evidence/0")).put("value", wireValue);
        assertDoesNotThrow(() -> analyzer.validateDiagnosis(d, prepared));
        ((ObjectNode) d.at("/evidence/0")).put("value", "1");
        assertThrows(IllegalArgumentException.class, () -> analyzer.validateDiagnosis(d, prepared));
        ((ObjectNode) d.at("/evidence/0")).put("field", "/relatedLogs/0/message")
                .put("value", prepared.at("/relatedLogs/0/message").asText());
        ((ObjectNode) d.at("/causeCandidates/0")).putArray("evidenceFields").add("/relatedLogs/0/message").add("/memoryLimitMb");
        assertDoesNotThrow(() -> analyzer.validateDiagnosis(d, prepared));
        ((ObjectNode) d.at("/evidence/0")).put("value", "Connection timeout");
        assertThrows(IllegalArgumentException.class, () -> analyzer.validateDiagnosis(d, prepared));
    }

    @Test void everyCandidateMustContainConfidenceCounterEvidenceAndChecks() throws Exception {
        var analyzer = new IncidentAnalyzer(null, null, "gpt-4.1-mini");
        for (String field : Set.of("confidence", "counterEvidence", "additionalChecks")) {
            ObjectNode d = diagnosis(); ((ObjectNode) d.at("/causeCandidates/0")).remove(field);
            assertThrows(IllegalArgumentException.class, () -> analyzer.validateDiagnosis(d, input()));
        }
        ObjectNode d = diagnosis(); ((ObjectNode) d.at("/causeCandidates/0/confidence")).put("level", "CERTAIN");
        assertThrows(IllegalArgumentException.class, () -> analyzer.validateDiagnosis(d, input()));
    }

    @Test void highNeedsMultipleSignalTypesAndKnownScopesAndOverallCannotExceedCandidates() throws Exception {
        var analyzer = new IncidentAnalyzer(null, null, "gpt-4.1-mini");
        ObjectNode d = diagnosis(); ((ObjectNode) d.path("confidence")).put("level", "HIGH");
        assertThrows(IllegalArgumentException.class, () -> analyzer.validateDiagnosis(d, input()));
        ((ObjectNode) d.at("/causeCandidates/0/confidence")).put("level", "HIGH");
        assertThrows(IllegalArgumentException.class, () -> analyzer.validateDiagnosis(d, input()));
        d.withArray("evidence").addObject().put("field", "/relatedLogs/0/message")
                .put("value", "Memory limit exceeded").put("interpretation", "메모리 오류 로그");
        ((ObjectNode) d.at("/causeCandidates/0")).withArray("evidenceFields").add("/relatedLogs/0/message");
        assertThrows(IllegalArgumentException.class, () -> analyzer.validateDiagnosis(d, input()));
        ObjectNode raw = input(); ObjectNode scopes = raw.putObject("metricScopes");
        scopes.set("memoryUsageMb", scope("MiB", "WINDOW_MAX", "api/instance-1/container", "rev-1"));
        scopes.set("memoryLimitMb", scopes.path("memoryUsageMb").deepCopy());
        assertDoesNotThrow(() -> analyzer.validateDiagnosis(d, raw));
        ((ObjectNode) scopes.path("memoryLimitMb")).put("target", "all-instances");
        assertThrows(IllegalArgumentException.class, () -> analyzer.validateDiagnosis(d, raw));
    }

    @Test void investigationDirectionAndInsufficientDataUseDifferentConditions() throws Exception {
        var analyzer = new IncidentAnalyzer(null, null, "gpt-4.1-mini");
        ObjectNode d = diagnosis(); ObjectNode candidate = (ObjectNode) d.at("/causeCandidates/0");
        candidate.put("code", "REQUEST_LATENCY").putArray("evidenceFields").add("/p95Latency");
        d.putArray("evidence").addObject().put("field", "/p95Latency").put("value", "0.8").put("interpretation", "관측된 P95");
        assertDoesNotThrow(() -> analyzer.validateDiagnosis(d, input()));
        ObjectNode missingP95 = input(); missingP95.remove("p95Latency");
        assertThrows(IllegalArgumentException.class, () -> analyzer.validateDiagnosis(d, missingP95));
        ((ObjectNode) candidate.path("confidence")).put("level", "HIGH");
        assertThrows(IllegalArgumentException.class, () -> analyzer.validateDiagnosis(d, input()));
        candidate.put("code", "INSUFFICIENT_DATA");
        assertThrows(IllegalArgumentException.class, () -> analyzer.validateDiagnosis(d, input()));
        ((ObjectNode) candidate.path("confidence")).put("level", "LOW");
        ((ObjectNode) d.path("confidence")).put("level", "LOW");
        assertDoesNotThrow(() -> analyzer.validateDiagnosis(d, input()));
        d.withArray("causeCandidates").add(candidate.deepCopy().put("code", "OTHER"));
        assertThrows(IllegalArgumentException.class, () -> analyzer.validateDiagnosis(d, input()));
    }

    @Test void discordShowsCandidateContractsWithinEmbedBudgetAndOldReportsStillRender() {
        ObjectNode d = diagnosis();
        var report = object(); report.set("diagnosis", d);
        var payload = IncidentReport.discordPayload(report);
        String candidate = payload.at("/embeds/0/fields/1/value").asText();
        assertTrue(candidate.contains("확신도: MEDIUM"));
        assertTrue(candidate.contains("반증/대안:"));
        assertTrue(candidate.contains("후보별 점검:"));
        ObjectNode old = d.deepCopy();
        ((ObjectNode) old.at("/causeCandidates/0")).remove(Set.of("confidence", "counterEvidence", "additionalChecks"));
        report.set("diagnosis", old);
        assertDoesNotThrow(() -> IncidentReport.discordPayload(report));
        for (String code : Set.of("DB_POOL_EXHAUSTION", "OTHER")) {
            ObjectNode candidateNode = ((ObjectNode) d.at("/causeCandidates/0")).deepCopy().put("code", code);
            candidateNode.put("summary", "가".repeat(600));
            ((ObjectNode) candidateNode.path("confidence")).put("reason", "나".repeat(600));
            candidateNode.putArray("counterEvidence").add("다".repeat(600));
            candidateNode.putArray("additionalChecks").add("라".repeat(600));
            d.withArray("causeCandidates").add(candidateNode);
        }
        report.set("diagnosis", d);
        var embed = IncidentReport.discordPayload(report).at("/embeds/0");
        int size = embed.path("title").asText().length() + embed.path("description").asText().length()
                + embed.at("/footer/text").asText().length();
        assertEquals(9, embed.path("fields").size());
        for (var field : embed.path("fields")) {
            assertTrue(field.path("value").asText().length() <= 1024);
            size += field.path("name").asText().length() + field.path("value").asText().length();
        }
        assertTrue(size <= 6000);
    }
}
