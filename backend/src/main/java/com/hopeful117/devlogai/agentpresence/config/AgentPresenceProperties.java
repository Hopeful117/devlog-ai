package com.hopeful117.devlogai.agentpresence.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent-presence")
public record AgentPresenceProperties(
        String baseUrl
) {
}
