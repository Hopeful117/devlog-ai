package com.hopeful117.devlogai.agentpresence.adapter.http;

public record AgentPresenceHttpRequest(
        String source,
        String title,
        String body,
        String level
) {
}
