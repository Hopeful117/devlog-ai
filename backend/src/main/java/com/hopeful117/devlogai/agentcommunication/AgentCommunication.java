package com.hopeful117.devlogai.agentcommunication;

public record AgentCommunication(
        String agent,
        AgentCommunicationIntent intent,
        String title,
        String body
) {
}
