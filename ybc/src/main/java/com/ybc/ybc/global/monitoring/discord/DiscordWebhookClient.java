package com.ybc.ybc.global.monitoring.discord;

import com.fasterxml.jackson.databind.JsonNode;
import com.ybc.ybc.global.monitoring.config.MonitoringProperties;
import com.ybc.ybc.global.monitoring.incident.IncidentHttp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Map;
import java.time.Duration;
import java.util.ArrayDeque;
import com.ybc.ybc.global.monitoring.incident.IncidentAlertTrigger;
import com.ybc.ybc.global.monitoring.incident.IncidentReport;

@Slf4j
@Component
public class DiscordWebhookClient {
    private static final int DISCORD_MESSAGE_MAX_LENGTH = 2_000;

    private final MonitoringProperties monitoringProperties;
    private final IncidentHttp alertHttp;
    private final ArrayDeque<String> pending = new ArrayDeque<>();

    @Autowired
    public DiscordWebhookClient(MonitoringProperties properties) {
        this(properties, IncidentHttp.create(Duration.ofSeconds(5)));
    }

    DiscordWebhookClient(MonitoringProperties properties, IncidentHttp http) {
        monitoringProperties = properties;
        alertHttp = http;
    }

    public void send(String message) {
        MonitoringProperties.Discord discord = monitoringProperties.discord();
        if (discord == null || !discord.enabled() || !StringUtils.hasText(discord.webhookUrl())) {
            return;
        }

        String safe = IncidentAlertTrigger.maskMessage(message);
        if (!IncidentAlertTrigger.isRequestActive()) {
            synchronized (pending) {
                if (pending.size() < 20) pending.addLast(safe);
                else log.warn("Deferred Discord buffer full; monitoring message dropped.");
            }
            return;
        }
        IncidentAlertTrigger.runMonitoring(() -> {
            try {
                alertHttp.post(URI.create(discord.webhookUrl()), Map.of(), IncidentReport.plainPayload(
                        buildContent(discord.mention(), safe), defaultIfBlank(discord.username(), "YBC Monitor")));
            } catch (Exception failure) {
                if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
                log.warn("Discord monitoring delivery unavailable; no automatic retry.");
            }
        });
    }

    public void flushPending() {
        if (!IncidentAlertTrigger.isRequestActive()) return;
        for (int count = 0; count < 2; count++) {
            String message;
            synchronized (pending) { message = pending.pollFirst(); }
            if (message == null) return;
            send(message);
        }
    }

    public String sendIncidentPayload(JsonNode payload, URI webhook) throws Exception { return sendIncidentPayload(payload, alertHttp, webhook); }

    /** Completes within the active HTTP request; never retries an uncertain POST automatically. */
    public String sendIncidentPayload(JsonNode payload, IncidentHttp http, URI webhook) throws Exception {
        JsonNode response = http.post(webhook, Map.of(), payload);
        if (!response.path("id").isTextual() || !response.path("id").asText().matches("[0-9]+")) {
            throw new IllegalArgumentException("Discord 메시지 확인 응답 없음. 채널에서 전송 여부를 확인하세요.");
        }
        return response.path("id").asText();
    }

    private String buildContent(String mention, String message) {
        String content = StringUtils.hasText(mention) ? mention + "\n" + message : message;
        if (content.length() <= DISCORD_MESSAGE_MAX_LENGTH) {
            return content;
        }

        return content.substring(0, DISCORD_MESSAGE_MAX_LENGTH - 3) + "...";
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }
}
