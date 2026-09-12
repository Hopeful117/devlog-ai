package com.hopeful117.devlogai.agentpresence;

public record AgentPresenceMessage(
        String title,
        String body,
        AgentPresenceLevel level
) {
}
