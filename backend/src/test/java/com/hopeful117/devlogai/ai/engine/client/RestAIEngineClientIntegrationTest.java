package com.hopeful117.devlogai.ai.engine.client;

import com.hopeful117.devlogai.ai.engine.dto.AiTaskSubmissionRequest;
import com.hopeful117.devlogai.ai.engine.dto.AiTaskSubmissionResponse;
import com.hopeful117.devlogai.ai.task.entity.AiTaskType;
import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.model.InsightType;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RestAIEngineClientIntegrationTest {

    @Test
    void shouldExchangeSubmissionContractOverHttp() throws IOException {
        UUID correlationId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        UUID externalJobId = UUID.randomUUID();
        AtomicReference<String> receivedBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0),
                0
        );
        server.createContext("/api/v1/ai/tasks", exchange -> respond(
                exchange,
                correlationId,
                externalJobId,
                receivedBody
        ));
        server.start();

        try {
            RestAIEngineClient client = new RestAIEngineClient(
                    RestClient.builder()
                            .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                            .build()
            );

            AiTaskSubmissionResponse response = client.submit(
                    request(correlationId, analysisId)
            );

            assertEquals(correlationId, response.correlationId());
            assertTrue(response.accepted());
            assertEquals(externalJobId.toString(), response.externalJobId());
            assertNotNull(response.acceptedAt());
            assertNotNull(receivedBody.get());
            assertTrue(receivedBody.get().contains(
                    "\"correlationId\":\"" + correlationId + "\""
            ));
            assertTrue(receivedBody.get().contains(
                    "\"taskType\":\"DECISION_PROPOSAL_GENERATION\""
            ));
            assertTrue(receivedBody.get().contains(
                    "\"analysisId\":\"" + analysisId + "\""
            ));
            assertTrue(receivedBody.get().contains("\"context\":"));
        } finally {
            server.stop(0);
        }
    }

    private void respond(
            HttpExchange exchange,
            UUID correlationId,
            UUID externalJobId,
            AtomicReference<String> receivedBody
    ) throws IOException {
        assertEquals("POST", exchange.getRequestMethod());
        receivedBody.set(new String(
                exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8
        ));
        byte[] response = """
                {
                  "correlationId": "%s",
                  "accepted": true,
                  "externalJobId": "%s",
                  "acceptedAt": "%s"
                }
                """.formatted(
                correlationId,
                externalJobId,
                Instant.parse("2026-07-21T20:00:00Z")
        ).getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(202, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private AiTaskSubmissionRequest request(
            UUID correlationId,
            UUID analysisId
    ) {
        AnalysisContext context = new AnalysisContext(
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
        return new AiTaskSubmissionRequest(
                correlationId,
                AiTaskType.DECISION_PROPOSAL_GENERATION,
                analysisId,
                new IntentDefinition("describe-project", "v1", "Describe", List.of(InsightType.PROJECT_PRESENTATION), List.of("traceable"), java.util.Map.of("type", "object"), "describe-project-prompt-v1"),
                context
        );
    }
}
