package com.ybc.ybc.global.monitoring.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ybc.ybc.global.monitoring.discord.DiscordWebhookClient;
import com.ybc.ybc.global.monitoring.incident.IncidentAlertTrigger;
import com.ybc.ybc.global.monitoring.incident.IncidentReport;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Locale;

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
