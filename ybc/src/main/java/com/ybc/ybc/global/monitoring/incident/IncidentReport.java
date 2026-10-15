package com.ybc.ybc.global.monitoring.incident;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.time.Instant;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.ybc.ybc.global.monitoring.incident.IncidentJson.*;

public final class IncidentReport {
    static final String NOTICE = "AI 원인 후보입니다. 담당자가 근거를 확인한 뒤 수동으로 대응하세요. 자동 설정 변경은 수행하지 않습니다.";

    private IncidentReport() { }

    static ObjectNode create(JsonNode raw, JsonNode input, String mode, Set<String> omitted,
                             IncidentAnalyzer.Result result, Instant started, long duration, String promptVersion) {
        ObjectNode report = object().put("incidentId", raw.path("incidentId").asText())
                .put("sourceType", raw.path("sourceType").asText()).put("inputSha256", IncidentJson.hash(raw))
                .put("mode", mode).put("promptVersion", promptVersion).put("model", result.model())
                .put("responseId", result.responseId()).put("analysisStartedAt", started.toString())
                .put("analysisCompletedAt", Instant.now().toString()).put("analysisDurationMs", duration)
                .put("humanReviewRequired", true).put("humanApprovalRequired", true).put("automaticChangesPerformed", false);
        report.set("omittedMetrics", MAPPER.valueToTree(omitted.stream().sorted().toList()));
        report.set("analyzedInput", input);
        report.set("diagnosis", result.diagnosis());
        report.set("usage", result.usage());
        if (raw.path("sourceType").asText().equals("LIVE")) report.set("measurementInput", raw);
        if (raw.hasNonNull("evidenceReference")) report.set("evidenceReference", raw.get("evidenceReference"));
        return (ObjectNode) IncidentInput.redact(report);
    }

    static Map<String, String> sections(JsonNode diagnosis) {
        Map<String, String> sections = new LinkedHashMap<>();
        sections.put("이상 현상", lines(diagnosis.path("anomalies")));
        int candidateIndex = 1;
        for (JsonNode candidate : diagnosis.path("causeCandidates")) {
            String value = candidate.path("code").asText() + ": " + candidate.path("summary").asText();
            // Retain display compatibility with previously checkpointed v1 reports.
            if (candidate.has("confidence")) value += "\n확신도: " + candidate.at("/confidence/level").asText()
                    + " — " + candidate.at("/confidence/reason").asText();
            value += "\n근거 경로: " + candidate.path("evidenceFields");
            if (candidate.has("counterEvidence")) value += "\n반증/대안:\n" + lines(candidate.path("counterEvidence"));
            if (candidate.has("additionalChecks")) value += "\n후보별 점검:\n" + lines(candidate.path("additionalChecks"));
            sections.put("원인 후보 " + candidateIndex++, value);
        }
        sections.put("판단 근거", StreamSupport.stream(diagnosis.path("evidence").spliterator(), false)
                .map(e -> "- " + e.path("field").asText() + " = " + e.path("value").asText()
                        + " → " + e.path("interpretation").asText()).collect(Collectors.joining("\n")));
        sections.put("서비스 영향", diagnosis.path("serviceImpact").asText());
        sections.put("추가 확인 항목", lines(diagnosis.path("additionalChecks")));
        StringBuilder actions = new StringBuilder();
        int index = 1;
        for (JsonNode action : diagnosis.path("recommendedActions")) actions.append(index++).append(". ").append(action.asText()).append('\n');
        sections.put("권장 대응 순서", actions.toString().strip());
        sections.put("확신도", diagnosis.path("confidence").path("level").asText() + " — "
                + diagnosis.path("confidence").path("reason").asText() + "\nHIGH도 원인 확정을 뜻하지 않습니다.");
        return sections;
    }

    public static ObjectNode discordPayload(JsonNode report) {
        ObjectNode payload = object();
        payload.putObject("allowed_mentions").putArray("parse");
        ObjectNode embed = payload.putArray("embeds").addObject();
        embed.put("title", clip("[" + report.path("sourceType").asText() + "] 장애 진단 " + report.path("incidentId").asText(), 220));
        embed.put("description", clip(NOTICE + "\n알림: " + report.at("/analyzedInput/alertName").asText("수동 분석")
                + "\n분석 자료: " + (IncidentInput.METRICS.stream().anyMatch(report.path("analyzedInput")::hasNonNull)
                ? "수집된 지표와 오류·경고 로그" : "오류·운영 경고 로그 표본 (Prometheus 지표 미수집)"), 500));
        embed.put("color", 0xE6A23C);
        var fields = embed.putArray("fields");
        sections(report.path("diagnosis")).forEach((name, value) -> fields.addObject()
                .put("name", name).put("value", clip(IncidentInput.redactText(value), name.startsWith("원인 후보") ? 650 : 500)).put("inline", false));
        embed.putObject("footer").put("text", "긴 항목은 일부 생략됩니다. 확신도는 정성 평가이며 최종 판단과 수정은 운영자가 수행합니다.");
        return payload;
    }

    public static ObjectNode plainPayload(String content, String username) {
        return object().put("content", content).put("username", username);
    }

    private static String lines(JsonNode values) {
        return StreamSupport.stream(values.spliterator(), false).map(value -> "- " + value.asText()).collect(Collectors.joining("\n"));
    }

    private static String clip(String text, int max) {
        if (text.length() <= max) return text;
        int end = max - 16;
        if (Character.isHighSurrogate(text.charAt(end - 1))) end--;
        return text.substring(0, end) + " …(일부 생략)";
    }
}
