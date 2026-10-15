package com.ybc.ybc.global.monitoring.incident;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static com.ybc.ybc.global.monitoring.incident.IncidentJson.*;

final class IncidentInput {
    static final Set<String> METRICS = Set.of("requestCount", "p95Latency", "status429Count", "status5xxCount",
            "memoryUsageMb", "memoryLimitMb", "activeInstanceCount", "maxInstanceCount",
            "dbActiveConnections", "dbMaxConnections", "jvmHeapUsageMb", "jvmHeapLimitMb", "baselineP95Latency");
    private static final Set<String> DECIMALS = Set.of("p95Latency", "memoryUsageMb", "memoryLimitMb",
            "jvmHeapUsageMb", "jvmHeapLimitMb", "baselineP95Latency");
    private static final Set<String> LIMITS = Set.of("memoryLimitMb", "maxInstanceCount", "dbMaxConnections", "jvmHeapLimitMb");
    private static final List<Pattern> SECRETS = List.of(
            Pattern.compile("(?i)(?:jdbc:)?(?:postgres(?:ql)?|mysql|redis|rediss)://[^\\s\"<>]+"),
            Pattern.compile("(?i)(?:[\"']?)(?:code|authorization_code)(?:[\"']?)\\s*[:=]\\s*(?:\"[^\"]*\"|'[^']*'|[^&\\s,;]+)"),
            Pattern.compile("(?i)https?://[^\\s\"<>]+"),
            Pattern.compile("(?i)\\bBearer\\s+[^\\s\",;]+"),
            Pattern.compile("(?i)\\b(?:authorization|cookie|set-cookie)\\s*[:=][^\\r\\n]+"),
            Pattern.compile("(?i)(?:[\"']?)(?:password|passwd|secret|api[-_]?key|access[-_]?token|refresh[-_]?token|token)(?:[\"']?)\\s*[:=]\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s,;]+)"),
            Pattern.compile("\\bsk-[A-Za-z0-9_-]+"),
            Pattern.compile("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"),
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"),
            Pattern.compile("(?<![0-9])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?![0-9])"),
            Pattern.compile("\\b01[016789][- ]?[0-9]{3,4}[- ]?[0-9]{4}\\b"));

    private IncidentInput() { }

    static ObjectNode prepare(JsonNode raw, String mode, Set<String> omitted) {
        validate(raw);
        require(Set.of("multi", "single-log").contains(mode), "mode는 multi 또는 single-log여야 합니다.");
        require(METRICS.containsAll(omitted), "omit에는 지원하는 지표 이름만 지정할 수 있습니다.");
        require(!mode.equals("single-log") || omitted.isEmpty(), "single-log와 omit은 함께 사용할 수 없습니다.");
        ObjectNode input = (ObjectNode) redact(raw);
        // Provenance references and simulation labels belong in the local report, not in model evidence.
        input.remove(List.of("incidentId", "sourceType", "evidenceReference"));
        if (mode.equals("single-log")) {
            require(!input.path("relatedLogs").isEmpty(), "single-log 모드에는 로그가 필요합니다.");
            input.remove(METRICS);
            input.set("relatedLogs", MAPPER.createArrayNode().add(input.path("relatedLogs").get(0)));
        } else {
            input.remove(omitted);
        }
        ObjectNode comparisons = IncidentMetricComparisons.policy(input);
        input.set("metricComparisons", comparisons);
        if (input.hasNonNull("baselineP95Latency")
                && !comparisons.path("baselineP95Latency").path("status").asText().equals("COMPARABLE")) {
            input.remove(List.of("baselineP95Latency", "baselineWindowStart", "baselineWindowEnd"));
        }
        require(METRICS.stream().anyMatch(key -> input.hasNonNull(key)) || !input.path("relatedLogs").isEmpty(),
                "분석할 지표 또는 로그가 필요합니다.");
        // Evidence values refer to the parsed wire input, not the original JSON file's spelling.
        ObjectNode normalized = (ObjectNode) parse(input.toString());
        ObjectNode numericEvidence = normalized.putObject("metricEvidenceValues");
        METRICS.stream().sorted().filter(normalized::hasNonNull).forEach(metric ->
                numericEvidence.put(metric, normalized.path(metric).asText()));
        return normalized;
    }

    static void validate(JsonNode raw) {
        Set<String> allowed = new HashSet<>(METRICS);
        allowed.addAll(Set.of("incidentId", "sourceType", "evidenceReference", "service", "revision",
                "windowStart", "windowEnd", "metricScope", "relatedLogs", "baselineWindowStart", "baselineWindowEnd",
                "countValuesEstimated", "collectionNotes", "alertName", "alertStartedAt", "metricScopes"));
        fields(raw, allowed);
        require(text(raw, "incidentId", 100).matches("[A-Za-z0-9][A-Za-z0-9._-]*"), "incidentId 형식이 올바르지 않습니다.");
        String source = text(raw, "sourceType", 20);
        require(Set.of("SIMULATION", "HISTORICAL", "LIVE").contains(source), "sourceType은 SIMULATION, HISTORICAL 또는 LIVE여야 합니다.");
        if (source.equals("HISTORICAL")) text(raw, "evidenceReference", 500);
        else if (raw.hasNonNull("evidenceReference")) text(raw, "evidenceReference", 500);
        text(raw, "service", 150);
        text(raw, "revision", 150);
        text(raw, "metricScope", 1000);
        IncidentMetricComparisons.validateScopes(raw);
        Instant start = instant(text(raw, "windowStart", 40));
        Instant end = instant(text(raw, "windowEnd", 40));
        require(start.isBefore(end) && Duration.between(start, end).compareTo(Duration.ofHours(24)) <= 0,
                "시간 구간은 0초 초과, 24시간 이하여야 합니다.");
        if (raw.hasNonNull("baselineP95Latency")) {
            Instant baselineStart = instant(text(raw, "baselineWindowStart", 40));
            Instant baselineEnd = instant(text(raw, "baselineWindowEnd", 40));
            require(baselineStart.isBefore(baselineEnd) && baselineEnd.equals(start)
                    && Duration.between(baselineStart, baselineEnd).equals(Duration.between(start, end)),
                    "P95 기준 구간은 직전의 동일 길이 구간이어야 합니다.");
        }
        if (raw.has("countValuesEstimated")) require(raw.path("countValuesEstimated").isBoolean(), "countValuesEstimated는 boolean이어야 합니다.");
        if (raw.has("alertName")) text(raw, "alertName", 150);
        if (raw.has("alertStartedAt")) instant(text(raw, "alertStartedAt", 40));
        if (raw.has("collectionNotes")) {
            require(raw.path("collectionNotes").isArray() && raw.path("collectionNotes").size() <= 25, "collectionNotes는 최대 25건이어야 합니다.");
            for (JsonNode note : raw.path("collectionNotes")) require(note.isTextual() && note.asText().length() <= 500, "수집 설명이 잘못됐습니다.");
        }
        for (String key : METRICS) {
            JsonNode value = raw.path(key);
            if (value.isMissingNode() || value.isNull()) continue;
            require(value.isNumber() && Double.isFinite(value.asDouble()) && value.asDouble() >= 0,
                    key + ": 0 이상의 유한 숫자가 필요합니다.");
            boolean estimate = raw.path("countValuesEstimated").asBoolean(false)
                    && Set.of("requestCount", "status429Count", "status5xxCount").contains(key);
            if (!DECIMALS.contains(key) && !estimate) require(value.isIntegralNumber() && value.canConvertToLong(), key + ": 정수가 필요합니다.");
            if (LIMITS.contains(key)) require(value.asDouble() > 0, key + ": 한도는 0보다 커야 합니다. 미수집 값은 null로 입력하세요.");
        }
        if (raw.hasNonNull("requestCount")) {
            double errors = raw.path("status429Count").asDouble(0) + raw.path("status5xxCount").asDouble(0);
            require(errors <= raw.path("requestCount").asDouble() + 0.000001, "동일 구간의 429 + 5xx 수가 전체 요청 수보다 많습니다.");
        }
        require(raw.path("relatedLogs").isArray() && raw.path("relatedLogs").size() <= 100,
                "relatedLogs는 최대 100건의 배열이어야 합니다.");
        for (JsonNode log : raw.path("relatedLogs")) {
            fields(log, Set.of("timestamp", "message"));
            Instant at = instant(text(log, "timestamp", 40));
            require(!at.isBefore(start) && at.isBefore(end), "로그 timestamp가 [windowStart, windowEnd) 밖에 있습니다.");
            text(log, "message", 2000);
        }
    }

    static Instant instant(String text) {
        try { return Instant.parse(text); }
        catch (DateTimeParseException e) { throw new IllegalArgumentException("시간은 UTC 또는 offset이 있는 ISO-8601 형식이어야 합니다."); }
    }

    static JsonNode redact(JsonNode node) {
        if (node.isTextual()) return TextNode.valueOf(redactText(node.asText()));
        if (node.isObject()) {
            ObjectNode copy = object();
            node.fields().forEachRemaining(entry -> copy.set(entry.getKey(), redact(entry.getValue())));
            return copy;
        }
        if (node.isArray()) {
            var copy = MAPPER.createArrayNode();
            node.forEach(value -> copy.add(redact(value)));
            return copy;
        }
        return node.deepCopy();
    }

    static String redactText(String value) {
        for (Pattern pattern : SECRETS) value = pattern.matcher(value).replaceAll("[REDACTED]");
        return value;
    }

    static String boundedLog(String message) {
        String redacted = redactText(message);
        return redacted.length() > 2000 ? redacted.substring(0, 1997) + "..." : redacted;
    }
}
