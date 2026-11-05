package com.ybc.ybc.global.monitoring.incident;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static com.ybc.ybc.global.monitoring.incident.IncidentJson.*;

@FunctionalInterface
public interface IncidentHttp {
    JsonNode post(URI uri, Map<String, String> headers, JsonNode body) throws IOException, InterruptedException;

    static IncidentHttp create() {
        return create(Duration.ofSeconds(30));
    }

    static IncidentHttp create(Duration timeout) {
        // Never follow redirects carrying API credentials; there is deliberately no automatic POST retry.
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        return (uri, headers, body) -> {
            HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "YBC-Incident-Diagnosis/1.0")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)));
            headers.forEach(request::header);
            // Errors never expose body, URL or credentials.
            HttpResponse<byte[]> response;
            byte[] data;
            try {
                response = client.send(request.build(), IncidentResponseBody.handler());
                data = response.body();
            } catch (IOException e) {
                throw new Failure("외부 API 연결 실패 또는 시간 초과. 전송 성공 여부를 확인한 뒤 다시 실행하세요.");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw Failure.fromResponse(response.statusCode(), data);
            }
            require(data.length <= MAX_FILE_BYTES, "외부 API 응답이 너무 큽니다.");
            String responseText = new String(data, StandardCharsets.UTF_8);
            return responseText.isBlank() ? object() : parse(responseText);
        };
    }

    enum QuotaFailure { CREDIT_BALANCE_EXHAUSTED, INSUFFICIENT_QUOTA }

    final class Failure extends IOException {
        private final int statusCode;
        private final QuotaFailure quotaFailure;
        Failure(String message) { this(message, 0); }
        Failure(String message, int statusCode) { this(message, statusCode, null); }
        private Failure(String message, int statusCode, QuotaFailure quotaFailure) {
            super(message);
            this.statusCode = statusCode;
            this.quotaFailure = quotaFailure;
        }
        int statusCode() { return statusCode; }
        QuotaFailure quotaFailure() { return quotaFailure; }

        static Failure fromResponse(int statusCode, byte[] body) {
            QuotaFailure quota = null;
            if (statusCode == 429 && body.length <= MAX_FILE_BYTES) {
                try {
                    JsonNode error = parse(new String(body, StandardCharsets.UTF_8)).path("error");
                    String code = error.path("code").asText();
                    if (code.equals("credit_balance_exhausted")) quota = QuotaFailure.CREDIT_BALANCE_EXHAUSTED;
                    else if (code.equals("insufficient_quota") || error.path("type").asText().equals("insufficient_quota")) {
                        quota = QuotaFailure.INSUFFICIENT_QUOTA;
                    }
                } catch (IllegalArgumentException ignored) {
                    // Non-JSON/unknown responses retain only the status; never retain provider text.
                }
            }
            return new Failure("외부 API HTTP " + statusCode + ". 자동 재시도하지 않았습니다.", statusCode, quota);
        }
    }
}
