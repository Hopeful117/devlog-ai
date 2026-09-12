package com.hopeful117.devlogai.agentcommunication.adapter.presence;

import com.hopeful117.devlogai.agentcommunication.AgentCommunication;
import com.hopeful117.devlogai.agentcommunication.AgentCommunicationIntent;
import com.hopeful117.devlogai.agentcommunication.AgentCommunicationPort;
import com.hopeful117.devlogai.agentpresence.AgentPresenceLevel;
import com.hopeful117.devlogai.agentpresence.AgentPresenceMessage;
import com.hopeful117.devlogai.agentpresence.AgentPresencePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AgentPresenceCommunicationAdapter implements AgentCommunicationPort {

    private final AgentPresencePort agentPresencePort;

    @Override
    public void communicate(AgentCommunication communication) {
        try {
            agentPresencePort.send(new AgentPresenceMessage(
                    communication.agent(),
                    communication.title(),
                    communication.body(),
                    presenceLevel(communication.intent())));
        } catch (RuntimeException exception) {
            log.warn("Agent Presence projection failed; communication dropped for agent {}: {}",
                    communication.agent(), exception.getMessage());
        }
    }

    private AgentPresenceLevel presenceLevel(AgentCommunicationIntent intent) {
        return switch (intent) {
            case INFORM, SUGGEST -> AgentPresenceLevel.INFO;
            case WARN -> AgentPresenceLevel.WARNING;
            case ASK -> AgentPresenceLevel.ATTENTION;
        };
    }
}
