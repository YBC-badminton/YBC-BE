package com.ybc.ybc.global.monitoring.service;

import com.ybc.ybc.global.monitoring.discord.DiscordWebhookClient;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class ErrorAlertService {
    private final DiscordWebhookClient discordWebhookClient;
    private final Environment environment;

    public void sendServerErrorAlert(HttpServletRequest request,
                                     HttpStatusCode statusCode,
                                     String errorCode,
                                     Throwable throwable) {
        String message = """
                [YBC Server Error]
                Time: %s
                Profiles: %s
                Status: %s
                Error Code: %s
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
