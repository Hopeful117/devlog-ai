package com.hopeful117.devlogai.agentpresence.adapter.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hopeful117.devlogai.agentpresence.AgentPresenceLevel;
import com.hopeful117.devlogai.agentpresence.AgentPresenceMessage;
import com.hopeful117.devlogai.agentpresence.AgentPresencePort;
import com.hopeful117.devlogai.agentpresence.config.AgentPresenceProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpAgentPresenceAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void postsExpectedJsonContractToMessageEndpointForEveryLevel() throws Exception {
        List<ReceivedRequest> received = new ArrayList<>();
        startServer(exchange -> {
            received.add(capture(exchange));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        AgentPresencePort adapter = adapter(serverBaseUrl(), objectMapper);

        for (AgentPresenceLevel level : AgentPresenceLevel.values()) {
            adapter.send(new AgentPresenceMessage(
                    "Build status", "Backend verification completed", level));
        }

        assertEquals(AgentPresenceLevel.values().length, received.size());
        for (int index = 0; index < AgentPresenceLevel.values().length; index++) {
            ReceivedRequest request = received.get(index);
            JsonNode payload = objectMapper.readTree(request.body());
            assertEquals("POST", request.method());
            assertEquals("/message", request.path());
            assertEquals("application/json", request.contentType());
            assertEquals(request.body().getBytes(StandardCharsets.UTF_8).length,
                    request.contentLength());
            assertEquals("DEVLOG", payload.get("source").asText());
            assertEquals("Build status", payload.get("title").asText());
            assertEquals("Backend verification completed", payload.get("body").asText());
            assertEquals(AgentPresenceLevel.values()[index].name(),
                    payload.get("level").asText());
        }
    }

    @Test
    void deliveryFailureDoesNotPropagate() throws IOException {
        int unavailablePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unavailablePort = socket.getLocalPort();
        }
        AgentPresencePort adapter = adapter(
                "http://127.0.0.1:" + unavailablePort, objectMapper);

        assertTimeout(Duration.ofSeconds(3), () -> assertDoesNotThrow(() ->
                adapter.send(new AgentPresenceMessage(
                        "Build status", "Device unavailable", AgentPresenceLevel.WARNING))));
    }

    @Test
    void serializationFailureDoesNotPropagate() throws Exception {
        ObjectMapper failingObjectMapper = mock(ObjectMapper.class);
        JsonProcessingException failure = new JsonProcessingException("serialization failed") { };
        when(failingObjectMapper.writeValueAsString(any())).thenThrow(failure);
        AgentPresencePort adapter = adapter("http://127.0.0.1:65535", failingObjectMapper);
        AgentPresenceMessage message = new AgentPresenceMessage(
                "Build status", "Serialization failed", AgentPresenceLevel.ATTENTION);

        assertDoesNotThrow(() -> adapter.send(message));

        verify(failingObjectMapper).writeValueAsString(any(AgentPresenceHttpRequest.class));
    }

    @Test
    void readTimeoutDoesNotPropagateAndRemainsBounded() throws IOException {
        startServer(exchange -> {
            try {
                Thread.sleep(Duration.ofSeconds(5));
                exchange.sendResponseHeaders(204, -1);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        AgentPresencePort adapter = adapter(serverBaseUrl(), objectMapper);

        assertTimeout(Duration.ofSeconds(4), () -> assertDoesNotThrow(() ->
                adapter.send(new AgentPresenceMessage(
                        "Build status", "Device timeout", AgentPresenceLevel.INFO))));
    }

    private HttpAgentPresenceAdapter adapter(String baseUrl, ObjectMapper mapper) {
        return new HttpAgentPresenceAdapter(new AgentPresenceProperties(baseUrl), mapper);
    }

    private void startServer(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler);
        server.start();
    }

    private String serverBaseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private ReceivedRequest capture(HttpExchange exchange) throws IOException {
        return new ReceivedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst("Content-Type"),
                Long.parseLong(exchange.getRequestHeaders().getFirst("Content-Length")),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
    }

    private record ReceivedRequest(
            String method,
            String path,
            String contentType,
            long contentLength,
            String body
    ) {
    }
}
