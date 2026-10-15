package com.ybc.ybc.global.monitoring.incident;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.ybc.ybc.global.monitoring.incident.IncidentFixtures.*;
import static com.ybc.ybc.global.monitoring.incident.IncidentJson.*;
import static org.junit.jupiter.api.Assertions.*;

class IncidentAnalyzerTest {
    @Test
    void sendsStrictSchemaWithoutToolsAndAcceptsGroundedResponse() throws Exception {
        AtomicBoolean called = new AtomicBoolean();
        var analyzer = new IncidentAnalyzer((uri, headers, request) -> {
            called.set(true);
            assertEquals("https://api.openai.com/v1/responses", uri.toString());
            assertEquals("Bearer test-secret", headers.get("Authorization"));
            assertTrue(request.at("/text/format/strict").asBoolean());
            assertFalse(request.path("store").asBoolean(true));
            assertFalse(request.has("tools"));
            assertFalse(request.path("input").asText().contains("SIMULATION"));
            return response(diagnosis());
        }, "test-secret", "gpt-4.1-mini");
        var result = analyzer.analyze(IncidentInput.prepare(input(), "multi", Set.of()));
        assertTrue(called.get());
        assertEquals("MEMORY_PRESSURE", result.diagnosis().at("/causeCandidates/0/code").asText());
    }

    @Test
    void rejectsHallucinatedValuesMissingEvidenceAndInventedFields() throws Exception {
        var analyzer = new IncidentAnalyzer(null, null, "gpt-4.1-mini");
        var d = diagnosis();
        ((ObjectNode) d.at("/evidence/0")).put("value", "999");
        assertThrows(IllegalArgumentException.class, () -> analyzer.validateDiagnosis(d, input()));
        assertThrows(IllegalArgumentException.class, () -> analyzer.validateDiagnosis(diagnosis(),
                IncidentInput.prepare(input(), "multi", Set.of("memoryUsageMb"))));
        assertThrows(IllegalArgumentException.class, () -> analyzer.validateDiagnosis(diagnosis().put("executeCommand", "change-memory"), input()));
        var unknown = diagnosis();
        ((ObjectNode) unknown.at("/causeCandidates/0")).putArray("evidenceFields").add("/status429Count");
        assertThrows(IllegalArgumentException.class, () -> analyzer.validateDiagnosis(unknown, input()));
    }

    @Test
    void incompleteRefusalAndMalformedResponsesNeverBecomeDiagnoses() throws Exception {
        var refusal = object().put("status", "completed");
        refusal.putArray("output").addObject().put("type", "message").putArray("content").addObject().put("type", "refusal").put("refusal", "No");
        for (var bad : new ObjectNode[]{response(diagnosis()).put("status", "incomplete"), refusal,
                response(object()), object().put("status", "completed")}) {
            var analyzer = new IncidentAnalyzer((u, h, b) -> bad, "test", "gpt-4.1-mini");
            assertThrows(IllegalArgumentException.class, () -> analyzer.analyze(input()));
        }
    }

    @Test
    void singleLogRequiresLowConfidence() throws Exception {
        var d = diagnosis();
        d.putArray("evidence").addObject().put("field", "/relatedLogs/0/message")
                .put("value", "Memory limit exceeded").put("interpretation", "초과 로그");
        ((ObjectNode) d.at("/causeCandidates/0")).putArray("evidenceFields").add("/relatedLogs/0/message");
        var single = IncidentInput.prepare(input(), "single-log", Set.of());
        var analyzer = new IncidentAnalyzer(null, null, "gpt-4.1-mini");
        assertThrows(IllegalArgumentException.class, () -> analyzer.validateDiagnosis(d, single));
        ((ObjectNode) d.path("confidence")).put("level", "LOW");
        ((ObjectNode) d.at("/causeCandidates/0/confidence")).put("level", "LOW");
        assertDoesNotThrow(() -> analyzer.validateDiagnosis(d, single));
    }

}
