package com.ybc.ybc.global.monitoring.incident;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import static com.ybc.ybc.global.monitoring.incident.IncidentJson.*;

final class IncidentFixtures {
    static ObjectNode input() {
        return (ObjectNode) parse("""
                {
                  "incidentId":"sim-memory", "sourceType":"SIMULATION", "service":"api", "revision":"rev-1",
                  "windowStart":"2026-08-01T10:00:00Z", "windowEnd":"2026-08-01T10:05:00Z",
                  "metricScope":"메모리: 단일 인스턴스 최대 MiB, 요청: 리비전 구간 합계",
                  "requestCount":120, "p95Latency":0.8, "status429Count":0, "status5xxCount":3,
                  "memoryUsageMb":531, "memoryLimitMb":512, "activeInstanceCount":1, "maxInstanceCount":3,
                  "dbActiveConnections":3, "dbMaxConnections":10,
                  "relatedLogs":[{"timestamp":"2026-08-01T10:02:00Z","message":"Memory limit exceeded"}]
                }
                """);
    }

    static ObjectNode diagnosis() {
        return (ObjectNode) parse("""
                {
                  "anomalies":["메모리 제한 초과 신호"],
                  "causeCandidates":[{"code":"MEMORY_PRESSURE","summary":"메모리 압박 가능성","evidenceFields":["/memoryUsageMb","/memoryLimitMb"],
                    "confidence":{"level":"MEDIUM","reason":"메모리 신호가 있으나 근본 원인은 추가 확인 필요"},
                    "counterEvidence":["다른 병목과 동시 발생 여부는 미확인"],
                    "additionalChecks":["메모리 범위와 프로세스 종료 시각 확인"]}],
                  "evidence":[
                    {"field":"/memoryUsageMb","value":"531","interpretation":"관측 메모리"},
                    {"field":"/memoryLimitMb","value":"512","interpretation":"설정된 한도"}
                  ],
                  "serviceImpact":"요청 처리 중단 가능성",
                  "additionalChecks":["인스턴스 종료 시각과 요청 실패 시각 비교"],
                  "recommendedActions":["담당자가 메모리 추세와 인스턴스 종료 로그 확인", "설정 변경 필요성을 검토한 뒤 수동 대응"],
                  "confidence":{"level":"MEDIUM","reason":"메모리 근거는 있으나 다른 병목은 추가 확인 필요"}
                }
                """);
    }

    static ObjectNode response(JsonNode diagnosis) {
        ObjectNode response = object().put("status", "completed").put("id", "resp_test").put("model", "gpt-4.1-mini");
        response.putObject("usage").put("input_tokens", 100).put("output_tokens", 100);
        response.putArray("output").addObject().put("type", "message").putArray("content")
                .addObject().put("type", "output_text").put("text", diagnosis.toString());
        return response;
    }
}
