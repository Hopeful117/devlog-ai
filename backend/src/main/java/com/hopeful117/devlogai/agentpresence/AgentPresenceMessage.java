package com.hopeful117.devlogai.agentpresence;

public record AgentPresenceMessage(
        String source,
        String title,
        String body,
        AgentPresenceLevel level
) {
}
