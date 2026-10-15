package com.ybc.ybc.global.monitoring.incident;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.Set;

import static com.ybc.ybc.global.monitoring.incident.IncidentJson.*;

/** Scope facts come from collectors; matching selector names alone cannot prove revision continuity. */
final class IncidentMetricComparisons {
    private static final Set<String> SCOPE_FIELDS = Set.of("unit", "aggregation", "target", "revision");
    static final Map<String, String> PAIRS = Map.of(
            "baselineP95Latency", "p95Latency", "memoryLimitMb", "memoryUsageMb",
            "jvmHeapLimitMb", "jvmHeapUsageMb", "dbMaxConnections", "dbActiveConnections",
            "maxInstanceCount", "activeInstanceCount");

    private IncidentMetricComparisons() { }

    static void validateScopes(JsonNode raw) {
        if (!raw.has("metricScopes")) return;
        JsonNode scopes = raw.path("metricScopes");
        fields(scopes, IncidentInput.METRICS);
        scopes.fields().forEachRemaining(entry -> {
            fields(entry.getValue(), SCOPE_FIELDS);
            for (String field : SCOPE_FIELDS) text(entry.getValue(), field, 500);
        });
    }

    static boolean knownScope(JsonNode scope) {
        return scope.isObject() && SCOPE_FIELDS.stream().allMatch(field -> {
            String value = scope.path(field).asText();
            return !value.isBlank() && !Set.of("UNKNOWN", "UNVERIFIED", "MIXED").contains(value);
        });
    }

    static ObjectNode comparison(JsonNode input, String left, String right) {
        if (!input.hasNonNull(left) || !input.hasNonNull(right)) return result("UNVERIFIED", "비교할 관측값이 누락됨");
        JsonNode leftScope = input.path("metricScopes").path(left), rightScope = input.path("metricScopes").path(right);
        // A known mismatch remains a mismatch even when another field is unknown.
        for (String field : SCOPE_FIELDS) {
            String a = leftScope.path(field).asText(), b = rightScope.path(field).asText();
            if (!a.isBlank() && !b.isBlank() && !a.equals("UNKNOWN") && !b.equals("UNKNOWN") && !a.equals(b)) {
                return result("INCOMPARABLE", field + " 불일치: 직접 비교/사용률/포화 판단 금지");
            }
        }
        if (!knownScope(leftScope) || !knownScope(rightScope)) return result("UNVERIFIED", "단위·집계 방식·대상·리비전 동일성 미확인");
        return result("COMPARABLE", "단위·집계 방식·대상·리비전 일치. 구간 최대값의 동시성이나 인과관계는 보장하지 않음");
    }

    static ObjectNode policy(JsonNode input) {
        ObjectNode policy = object();
        PAIRS.forEach((right, left) -> policy.set(right, comparison(input, left, right)));
        return policy;
    }

    private static ObjectNode result(String status, String reason) { return object().put("status", status).put("reason", reason); }
}
