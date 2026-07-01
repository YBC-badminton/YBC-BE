package com.ybc.ybc.global.monitoring.discord;

import com.ybc.ybc.global.monitoring.config.MonitoringProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DiscordWebhookClient {
    private static final int DISCORD_MESSAGE_MAX_LENGTH = 2_000;

    private final RestClient restClient;
    private final MonitoringProperties monitoringProperties;

    @Async
    public void send(String message) {
        MonitoringProperties.Discord discord = monitoringProperties.discord();
        if (discord == null || !discord.enabled() || !StringUtils.hasText(discord.webhookUrl())) {
            return;
        }

        String content = buildContent(discord.mention(), message);

        try {
            restClient.post()
                    .uri(discord.webhookUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "username", defaultIfBlank(discord.username(), "YBC Monitor"),
                            "content", content
                    ))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception exception) {
            log.warn("Failed to send Discord alert", exception);
        }
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
