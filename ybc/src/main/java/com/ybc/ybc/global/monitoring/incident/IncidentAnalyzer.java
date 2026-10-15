package com.ybc.ybc.global.monitoring.incident;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.ybc.ybc.global.monitoring.incident.IncidentJson.*;

final class IncidentAnalyzer {
    static final String PROMPT_VERSION = "ybc-incident-v6";
    private final IncidentHttp http;
    private final String apiKey;
    private final String model;
    private final JsonNode schema;
    private final String instructions;
    private final String promptVersion;

    IncidentAnalyzer(IncidentHttp http, String apiKey, String model) throws IOException {
        this.http = http;
        this.apiKey = apiKey;
        require(model != null && model.matches("[A-Za-z0-9._-]{1,100}"), "OPENAI_INCIDENT_MODEL 형식이 올바르지 않습니다.");
        this.model = model;
        this.promptVersion = PROMPT_VERSION;
        schema = parse(resource("diagnosis-schema.json"));
        instructions = resource("instructions.txt");
    }

    String promptVersion() { return promptVersion; }

    boolean isConfigured() { return apiKey != null && apiKey.matches("[\\x21-\\x7E]+"); }

    ObjectNode request(JsonNode input) {
        ObjectNode request = object();
        request.put("model", model).put("store", false).put("max_output_tokens", 6000);
        request.put("instructions", instructions);
        request.put("input", input.toString());
        request.putObject("text").putObject("format")
                .put("type", "json_schema").put("name", "incident_diagnosis").put("strict", true).set("schema", schema);
        return request;
    }

    Result analyze(JsonNode input) throws IOException, InterruptedException {
        require(apiKey != null && !apiKey.isBlank(), "OPENAI_API_KEY 환경 변수가 필요합니다.");
        require(apiKey.matches("[\\x21-\\x7E]+"), "OPENAI_API_KEY에 공백 또는 허용되지 않은 문자가 있습니다.");
        JsonNode response = http.post(URI.create("https://api.openai.com/v1/responses"),
                Map.of("Authorization", "Bearer " + apiKey), request(input));
        require(response.path("status").asText().equals("completed"), "AI 응답이 완료되지 않았습니다. 결과를 진단으로 사용하지 않습니다.");
        String json = null;
        for (JsonNode item : response.path("output")) {
            if (!item.path("type").asText().equals("message")) continue;
            for (JsonNode content : item.path("content")) {
                require(!content.path("type").asText().equals("refusal"), "AI가 분석을 거절했습니다.");
                if (content.path("type").asText().equals("output_text")) {
                    require(json == null, "AI가 여러 진단을 반환했습니다.");
                    json = content.path("text").asText();
                }
            }
        }
        require(json != null, "AI 응답에 진단 JSON이 없습니다.");
        JsonNode diagnosis = parse(json);
        validateDiagnosis(diagnosis, input);
        return new Result(diagnosis, response.path("id").asText(), response.path("model").asText(model), response.path("usage"));
    }

    void validateDiagnosis(JsonNode diagnosis, JsonNode input) {
        validate(diagnosis, schema);
        JsonNode wireInput = parse(input.toString());
        Set<String> evidenceFields = new HashSet<>();
        for (JsonNode evidence : diagnosis.path("evidence")) {
            String field = evidence.path("field").asText();
            require(field.matches("/relatedLogs/[0-9]+/message") ||
                    IncidentInput.METRICS.stream().anyMatch(metric -> field.equals("/" + metric)), "허용되지 않은 근거 경로입니다.");
            JsonNode actual = wireInput.at(field);
            require(!actual.isMissingNode() && !actual.isNull() && actual.isValueNode()
                    && actual.asText().equals(evidence.path("value").asText()), "AI 근거 값이 실제 입력과 일치하지 않습니다.");
            require(evidenceFields.add(field), "AI 근거 경로가 중복됐습니다.");
            if (field.equals("/baselineP95Latency")) require(
                    IncidentMetricComparisons.comparison(wireInput, "p95Latency", "baselineP95Latency")
                            .path("status").asText().equals("COMPARABLE"), "범위가 확인되지 않은 baseline을 근거로 사용할 수 없습니다.");
        }
        Set<String> codes = new HashSet<>();
        int highestCandidateConfidence = 0;
        boolean logsOnly = IncidentInput.METRICS.stream().noneMatch(input::hasNonNull);
        for (JsonNode candidate : diagnosis.path("causeCandidates")) {
            String code = candidate.path("code").asText();
            require(codes.add(code), "AI 원인 후보 코드가 중복됐습니다.");
            Set<String> candidateFields = new HashSet<>();
            for (JsonNode field : candidate.path("evidenceFields")) {
                require(evidenceFields.contains(field.asText()), "원인 후보가 검증되지 않은 근거를 참조합니다.");
                require(candidateFields.add(field.asText()), "후보의 근거 경로가 중복됐습니다.");
            }
            int confidence = confidenceRank(candidate.at("/confidence/level").asText());
            highestCandidateConfidence = Math.max(highestCandidateConfidence, confidence);
            if (logsOnly) require(confidence == 0, "지표 없는 로그 후보의 확신도는 LOW여야 합니다.");
            if (code.equals("REQUEST_LATENCY")) {
                require(wireInput.path("p95Latency").isNumber() && wireInput.path("p95Latency").asDouble() > 0
                        && candidateFields.contains("/p95Latency"), "REQUEST_LATENCY에는 관측된 P95 근거가 필요합니다.");
                require(confidence < 2, "REQUEST_LATENCY는 조사 방향이며 HIGH 원인 후보가 아닙니다.");
            }
            if (code.equals("INSUFFICIENT_DATA")) require(diagnosis.path("causeCandidates").size() == 1
                    && confidence == 0 && diagnosis.at("/confidence/level").asText().equals("LOW"),
                    "INSUFFICIENT_DATA는 단독 후보이며 후보/전체 확신도는 LOW여야 합니다.");
            if (confidence == 2) {
                require(candidateFields.stream().map(IncidentAnalyzer::signalCategory).distinct().count() >= 2,
                        "HIGH 후보에는 서로 다른 종류의 관측 근거가 필요합니다.");
                for (String field : candidateFields) {
                    if (!field.startsWith("/relatedLogs/")) require(IncidentMetricComparisons.knownScope(
                            wireInput.path("metricScopes").path(field.substring(1))), "범위가 미확인인 지표로 HIGH를 사용할 수 없습니다.");
                }
                for (var pair : IncidentMetricComparisons.PAIRS.entrySet()) {
                    if (candidateFields.contains("/" + pair.getKey()) && candidateFields.contains("/" + pair.getValue())) require(
                            IncidentMetricComparisons.comparison(wireInput, pair.getValue(), pair.getKey())
                                    .path("status").asText().equals("COMPARABLE"), "비교 범위 불일치로 HIGH를 사용할 수 없습니다.");
                }
            }
        }
        require(confidenceRank(diagnosis.at("/confidence/level").asText()) <= highestCandidateConfidence,
                "전체 확신도는 후보별 확신도의 최댓값을 넘을 수 없습니다.");
        if (logsOnly) {
            require(diagnosis.path("confidence").path("level").asText().equals("LOW"), "지표 없는 로그 진단의 확신도는 LOW여야 합니다.");
        }
    }

    private static int confidenceRank(String level) {
        return switch (level) { case "HIGH" -> 2; case "MEDIUM" -> 1; default -> 0; };
    }

    private static String signalCategory(String field) {
        if (field.startsWith("/relatedLogs/")) return "log";
        if (field.startsWith("/db")) return "db-pool";
        if (field.contains("Heap") || field.startsWith("/memory")) return "memory";
        if (field.contains("Instance")) return "instances";
        return "http";
    }

    private static String resource(String name) throws IOException {
        try (var stream = IncidentAnalyzer.class.getResourceAsStream("/incident/" + name)) {
            if (stream == null) throw new IOException("진단 리소스를 찾을 수 없습니다.");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    record Result(JsonNode diagnosis, String responseId, String model, JsonNode usage) { }
}
