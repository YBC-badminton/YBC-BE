package com.ybc.ybc.global.monitoring.incident;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;

import static com.ybc.ybc.global.monitoring.incident.IncidentJson.*;

/** In-memory logs from this JVM, anchored to the original alert time even when delivery was deferred. */
final class ApplicationIncidentCollector {
    ObjectNode collect(JsonNode event) {
        Instant end = IncidentInput.instant(event.path("receivedAt").asText());
        Instant start = end.minusSeconds(300);
        ObjectNode raw = object().put("incidentId", "incident-" + event.path("jobId").asText()).put("sourceType", "LIVE")
                .put("service", event.path("job").asText()).put("revision", event.path("revision").asText("UNKNOWN"))
                .put("alertName", event.path("alertName").asText()).put("alertStartedAt", event.path("startsAt").asText())
                .put("windowStart", start.toString()).put("windowEnd", end.toString())
                .put("metricScope", "이 JVM 경고 시점 직전 5분의 마스킹된 오류·운영 경고 로그 표본. 다른 인스턴스/전체 로그 아님.");
        var notes = raw.putArray("collectionNotes");
        notes.add("최대 100건·메시지 2000자의 JVM 메모리 표본. 재시작/버퍼 초과로 누락 가능. 분석 실행 시점의 현재 상태와 다를 수 있음.");
        notes.add("메모리 경고는 cgroup 단일 시점 관측이며 JVM Heap과 범위가 다름. DB 용량과 연결 풀은 서로 다른 자원 범위.");
        notes.add("HTTP 경고는 로그에 명시된 JVM 완료 요청 구간의 통계. P95는 [하한,상한) 히스토그램 구간이며 정확한 숫자가 아님.");
        notes.add("요청이 없는 동안 감시하지 않음. 누락 자료는 0/정상으로 해석하지 않고 로그 근거의 확신도는 LOW.");
        ArrayNode logs = event.path("relatedLogs").isArray() ? ((ArrayNode) event.path("relatedLogs")).deepCopy() : MAPPER.createArrayNode();
        String message = IncidentInput.boundedLog(event.path("triggerMessage").asText());
        String at = event.path("startsAt").asText();
        boolean present = false;
        for (JsonNode entry : logs) if (entry.path("timestamp").asText().equals(at) && entry.path("message").asText().equals(message)) present = true;
        if (!present && !message.isBlank()) {
            if (logs.size() == 100) logs.remove(0);
            logs.add(object().put("timestamp", at).put("message", message));
        }
        raw.set("relatedLogs", logs);
        raw = (ObjectNode) IncidentInput.redact(raw);
        IncidentInput.validate(raw);
        return raw;
    }
}
