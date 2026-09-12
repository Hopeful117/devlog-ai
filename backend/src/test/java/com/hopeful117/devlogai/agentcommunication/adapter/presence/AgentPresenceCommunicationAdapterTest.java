package com.hopeful117.devlogai.agentcommunication.adapter.presence;

import com.hopeful117.devlogai.agentcommunication.AgentCommunication;
import com.hopeful117.devlogai.agentcommunication.AgentCommunicationIntent;
import com.hopeful117.devlogai.agentcommunication.AgentCommunicationPort;
import com.hopeful117.devlogai.agentpresence.AgentPresenceLevel;
import com.hopeful117.devlogai.agentpresence.AgentPresenceMessage;
import com.hopeful117.devlogai.agentpresence.AgentPresencePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AgentPresenceCommunicationAdapterTest {

    @Mock
    private AgentPresencePort agentPresencePort;

    @ParameterizedTest
    @MethodSource("intentMappings")
    void projectsCommunicationIntentAndPreservesContent(
            AgentCommunicationIntent intent,
            AgentPresenceLevel expectedLevel
    ) {
        AgentCommunicationPort communicationPort =
                new AgentPresenceCommunicationAdapter(agentPresencePort);

        communicationPort.communicate(
                new AgentCommunication("DEVLOG-REVIEWER", intent, "Title", "Body"));

        ArgumentCaptor<AgentPresenceMessage> message =
                ArgumentCaptor.forClass(AgentPresenceMessage.class);
        verify(agentPresencePort).send(message.capture());
        assertEquals(new AgentPresenceMessage(
                "DEVLOG-REVIEWER", "Title", "Body", expectedLevel), message.getValue());
    }

    @Test
    void presenceFailureDoesNotPropagateAcrossCommunicationBoundary() {
        AgentCommunicationPort communicationPort =
                new AgentPresenceCommunicationAdapter(agentPresencePort);
        doThrow(new IllegalStateException("surface unavailable"))
                .when(agentPresencePort).send(any());

        assertDoesNotThrow(() -> communicationPort.communicate(new AgentCommunication(
                "DEVLOG", AgentCommunicationIntent.WARN, "Warning", "Surface unavailable")));

        verify(agentPresencePort).send(any(AgentPresenceMessage.class));
    }

    private static Stream<Arguments> intentMappings() {
        return Stream.of(
                Arguments.of(AgentCommunicationIntent.INFORM, AgentPresenceLevel.INFO),
                Arguments.of(AgentCommunicationIntent.SUGGEST, AgentPresenceLevel.INFO),
                Arguments.of(AgentCommunicationIntent.WARN, AgentPresenceLevel.WARNING),
                Arguments.of(AgentCommunicationIntent.ASK, AgentPresenceLevel.ATTENTION));
    }
}
