package com.ybc.ybc.global.monitoring.incident;

import java.net.URI;

import static com.ybc.ybc.global.monitoring.incident.IncidentJson.*;

final class IncidentDiscord {
    private IncidentDiscord() { }

    static URI webhook(String value) {
        require(value != null && !value.isBlank(), "DISCORD_INCIDENT_WEBHOOK_URL 환경 변수가 필요합니다.");
        URI uri;
        try { uri = URI.create(value); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Discord Webhook URL 형식이 올바르지 않습니다."); }
        require("https".equals(uri.getScheme()) && "discord.com".equals(uri.getHost())
                        && uri.getPort() == -1 && uri.getUserInfo() == null && uri.getRawQuery() == null && uri.getFragment() == null
                        && uri.getRawPath().matches("/api/(?:v[0-9]+/)?webhooks/[0-9]+/[A-Za-z0-9._-]+"),
                "discord.com의 HTTPS Webhook URL을 query 없이 설정하세요. 일반 텍스트 채널을 사용합니다.");
        return URI.create(value + "?wait=true");
    }

}
