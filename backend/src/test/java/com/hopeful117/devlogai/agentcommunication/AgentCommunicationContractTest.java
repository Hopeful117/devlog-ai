package com.hopeful117.devlogai.agentcommunication;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AgentCommunicationContractTest {

    @Test
    void communicationExpressesAgentIntentTitleAndBody() {
        AgentCommunication communication = new AgentCommunication(
                "DEVLOG", AgentCommunicationIntent.ASK,
                "Architecture question", "Should this boundary remain synchronous?");

        assertEquals("DEVLOG", communication.agent());
        assertEquals(AgentCommunicationIntent.ASK, communication.intent());
        assertEquals("Architecture question", communication.title());
        assertEquals("Should this boundary remain synchronous?", communication.body());
    }

    @Test
    void modelAndBoundaryPublicSignaturesAreIndependentOfPresenceAndHttp() {
        Stream<Class<?>> signatureTypes = Stream.concat(
                Arrays.stream(AgentCommunication.class.getRecordComponents())
                        .map(component -> component.getType()),
                Arrays.stream(AgentCommunicationPort.class.getDeclaredMethods())
                        .flatMap(method -> Stream.concat(
                                Stream.of(method.getReturnType()),
                                Arrays.stream(method.getParameterTypes()))));

        assertFalse(signatureTypes.map(Class::getPackageName).anyMatch(packageName ->
                packageName.contains("agentpresence")
                        || packageName.contains("http")));
    }
}
