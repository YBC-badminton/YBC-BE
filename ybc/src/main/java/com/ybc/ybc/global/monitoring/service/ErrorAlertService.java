package com.ybc.ybc.global.monitoring.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ybc.ybc.global.monitoring.discord.DiscordWebhookClient;
import com.ybc.ybc.global.monitoring.incident.IncidentAlertTrigger;
import com.ybc.ybc.global.monitoring.incident.IncidentReport;
import com.ybc.ybc.global.monitoring.incident.IncidentHttp;
import com.ybc.ybc.global.monitoring.dto.response.MonitoringIncidentTestResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ErrorAlertService {
    private final DiscordWebhookClient discordWebhookClient;
    private final Environment environment;
    private final IncidentAlertTrigger incidentAlertTrigger;

    public void sendServerErrorAlert(HttpServletRequest request,
                                     HttpStatusCode statusCode,
                                     String errorCode,
                                     Throwable throwable) {
        String message = """
                [YBC Server Error]
                Time: %s
                Profiles: %s
                Status: %s
                Error Type: %s
                Method: %s
                URI: %s
                Exception: %s
                Message: %s
                """.formatted(
                ZonedDateTime.now(),
                activeProfiles(),
                statusCode.value(),
                errorCode,
                request.getMethod(),
                request.getRequestURI(),
                throwable.getClass().getSimpleName(),
                defaultMessage(throwable)
        );

        discordWebhookClient.send(message);
        incidentAlertTrigger.submit("HTTP_5XX", exceptionEvidence(throwable));
    }

    public void sendAsyncExceptionAlert(String className,
                                        String methodName,
                                        String parameters,
                                        Throwable throwable) {
        String message = """
                [YBC Async Error]
                Time: %s
                Profiles: %s
                Method: %s#%s
                Params: %s
                Exception: %s
                Message: %s
                """.formatted(
                ZonedDateTime.now(),
                activeProfiles(),
                className,
                methodName,
                parameters,
                throwable.getClass().getSimpleName(),
                defaultMessage(throwable)
        );

        discordWebhookClient.send(message);
        incidentAlertTrigger.submit("ASYNC_ERROR", exceptionEvidence(throwable));
    }

    public void sendSupabaseQuotaAlert(long databaseSizeBytes,
                                       long databaseLimitBytes,
                                       int usagePercent,
                                       String severity) {
        String message = """
                [YBC Supabase Quota Alert]
                Time: %s
                Profiles: %s
                Severity: %s
                Database Size: %s MB
                Database Limit: %s MB
                Usage: %s%%
                """.formatted(
                ZonedDateTime.now(),
                activeProfiles(),
                severity,
                toMegabytes(databaseSizeBytes),
                toMegabytes(databaseLimitBytes),
                usagePercent
        );

        discordWebhookClient.send(message);
        incidentAlertTrigger.submit("DB_QUOTA_" + severity,
                "DB size=" + databaseSizeBytes + " bytes, limit=" + databaseLimitBytes + " bytes, usage=" + usagePercent + "%");
    }

    public void sendSupabaseQuotaRecoveredAlert(long databaseSizeBytes,
                                                long databaseLimitBytes,
                                                int usagePercent) {
        String message = """
                [YBC Supabase Quota Recovered]
                Time: %s
                Profiles: %s
                Database Size: %s MB
                Database Limit: %s MB
                Usage: %s%%
                """.formatted(
                ZonedDateTime.now(),
                activeProfiles(),
                toMegabytes(databaseSizeBytes),
                toMegabytes(databaseLimitBytes),
                usagePercent
        );

        discordWebhookClient.send(message);
    }

    public void sendSupabaseMonitorFailureAlert(Throwable throwable) {
        String message = """
                [YBC Supabase Monitor Failure]
                Time: %s
                Profiles: %s
                Exception: %s
                Message: %s
                """.formatted(
                ZonedDateTime.now(),
                activeProfiles(),
                throwable.getClass().getSimpleName(),
                defaultMessage(throwable)
        );

        discordWebhookClient.send(message);
        incidentAlertTrigger.submit("DB_MONITOR_FAILURE", exceptionEvidence(throwable));
    }

    public void sendMemoryWarningAlert(long usedBytes, long thresholdBytes, Long limitBytes, String scope) {
        String evidence = "Memory scope=" + scope + ", used=" + usedBytes + " bytes, warningThreshold=" + thresholdBytes
                + " bytes, cgroupLimit=" + (limitBytes == null ? "UNKNOWN" : limitBytes + " bytes")
                + ". Cgroup accounting includes native memory and page cache; JVM heap has a different scope. Cloud Run charts may use different sampling windows.";
        String message = """
                [YBC Memory Warning]
                Time: %s
                Profiles: %s
                Scope: %s
                Used: %s GB (%d bytes)
                Warning Threshold: %s GB (%d bytes)
                Cgroup Limit: %s
                """.formatted(ZonedDateTime.now(), activeProfiles(), scope, gigabytes(usedBytes), usedBytes,
                gigabytes(thresholdBytes), thresholdBytes, limitBytes == null ? "UNKNOWN" : gigabytes(limitBytes) + " GB");
        discordWebhookClient.send(message);
        incidentAlertTrigger.submit("HIGH_CONTAINER_MEMORY", evidence);
    }

    public void sendMemoryRecoveredAlert(long usedBytes, long recoveryThresholdBytes, String scope) {
        discordWebhookClient.send("""
                [YBC Memory Recovered]
                Time: %s
                Scope: %s
                Used: %s GB (%d bytes)
                Recovery Threshold: %s GB
                """.formatted(ZonedDateTime.now(), scope, gigabytes(usedBytes), usedBytes, gigabytes(recoveryThresholdBytes)));
    }

    private String gigabytes(long bytes) { return String.format(Locale.ROOT, "%.3f", bytes / 1_000_000_000.0); }

    public void sendOperationalWarning(String alertName, String summary, String evidence) {
        discordWebhookClient.send("""
                [YBC Operational Warning]
                Time: %s
                Profiles: %s
                Alert: %s
                Summary: %s
                Evidence: %s
                """.formatted(ZonedDateTime.now(), activeProfiles(), alertName, summary, evidence));
        incidentAlertTrigger.submit(alertName, evidence);
    }

    public void sendOperationalRecovery(String alertName, String evidence) {
        discordWebhookClient.send("""
                [YBC Operational Recovered]
                Time: %s
                Alert: %s
                Evidence: %s
                """.formatted(ZonedDateTime.now(), alertName, evidence));
    }

    public void sendManualTestAlert(HttpServletRequest request, String triggerSource) {
        String message = """
                [YBC Manual Alert Test]
                Time: %s
                Profiles: %s
                Trigger: %s
                Method: %s
                URI: %s
                Remote Addr: %s
                """.formatted(
                ZonedDateTime.now(),
                activeProfiles(),
                triggerSource,
                request.getMethod(),
                request.getRequestURI(),
                request.getRemoteAddr()
        );

        discordWebhookClient.send(message);
    }

    public String sendIncidentDiagnosisAlert(JsonNode report, URI webhook) throws Exception {
        return discordWebhookClient.sendIncidentPayload(IncidentReport.discordPayload(report), webhook);
    }

    public void sendIncidentQuotaAlert(IncidentHttp.QuotaFailure reason) {
        String summary = switch (reason) {
            case CREDIT_BALANCE_EXHAUSTED -> "OpenAI API 크레딧이 소진되어 GPT 장애 분석을 전송하지 못했습니다.";
            case INSUFFICIENT_QUOTA -> "OpenAI API 크레딧 또는 사용 한도 부족으로 GPT 장애 분석을 전송하지 못했습니다.";
        };
        // Send directly: this notification must never trigger GPT analysis of its own failure.
        discordWebhookClient.send("""
                [YBC GPT Analysis Unavailable]
                Time: %s
                Profiles: %s
                Reason: %s
                %s
                조치: 운영 API 키가 속한 조직의 크레딧 잔액과 사용 한도를 확인해 주세요.
                확인 위치: OpenAI Platform → Settings → Organization → Billing
                원본 오류·경고 알림은 별도로 유지됩니다. 실패한 분석은 자동 재시도하지 않습니다.
                이 안내는 서버 인스턴스별 최대 30분에 한 번 전송을 시도합니다.
                """.formatted(ZonedDateTime.now(), activeProfiles(), reason.name(), summary));
    }

    public MonitoringIncidentTestResponse sendManualIncidentTestAlert() {
        String testId = UUID.randomUUID().toString();
        String evidence = """
                [SIMULATION / 연결 점검용 가상 사건]
                Test ID: %s
                Scenario: ADMIN_ACCESS_DENIED
                Actor: synthetic-member-001 (가상 회원, 운영진 아님)
                Role: MEMBER
                Method: GET
                URI: /admin/monitoring-probe (가상 경로)
                Status: 403
                Authorization result: DENIED (접근 차단 성공을 가정한 합성 로그)
                실제 접근/공격/정보 유출이 아님. 이 단일 사건만으로 악의적인 의도나 침해를 단정할 수 없음.
                """.formatted(testId);
        return new MonitoringIncidentTestResponse(testId, "ADMIN_ACCESS_DENIED", true,
                incidentAlertTrigger.testAdminAccessDenied(testId, evidence));
    }

    private String exceptionEvidence(Throwable throwable) {
        StringBuilder evidence = new StringBuilder();
        Throwable current = throwable;
        for (int depth = 0; current != null && depth < 3; depth++, current = current.getCause()) {
            evidence.append(current.getClass().getSimpleName()).append(": ").append(defaultMessage(current)).append('\n');
            // Stack locations help locate failures without collecting request parameters/bodies/headers.
            Arrays.stream(current.getStackTrace()).limit(3).forEach(frame -> evidence.append(" at ").append(frame).append('\n'));
        }
        return evidence.toString();
    }

    private String activeProfiles() {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length == 0) {
            activeProfiles = environment.getDefaultProfiles();
        }
        return Arrays.toString(activeProfiles);
    }

    private long toMegabytes(long bytes) {
        return Math.round(bytes / 1024.0 / 1024.0);
    }

    private String defaultMessage(Throwable throwable) {
        return throwable.getMessage() == null ? "(no message)" : throwable.getMessage();
    }
}
