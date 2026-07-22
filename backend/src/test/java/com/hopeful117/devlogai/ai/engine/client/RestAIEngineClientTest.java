package com.hopeful117.devlogai.ai.engine.client;

import com.hopeful117.devlogai.ai.engine.dto.AiTaskSubmissionRequest;
import com.hopeful117.devlogai.ai.engine.dto.AiTaskSubmissionResponse;
import com.hopeful117.devlogai.ai.engine.exception.AIEngineCommunicationException;
import com.hopeful117.devlogai.ai.task.entity.AiTaskType;
import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.model.InsightType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class RestAIEngineClientTest {

    private MockRestServiceServer server;
    private RestAIEngineClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestAIEngineClient(
                builder.baseUrl("http://ai-engine.test").build()
        );
    }

    @Test
    void shouldSubmitCompleteContractAndAcceptAcknowledgement() {
        UUID correlationId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        Instant acceptedAt = Instant.parse("2026-07-21T18:00:00Z");
        AiTaskSubmissionRequest request = request(correlationId, analysisId);

        server.expect(once(), requestTo("http://ai-engine.test/api/v1/ai/tasks"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.correlationId").value(correlationId.toString()))
                .andExpect(jsonPath("$.taskType")
                        .value("DECISION_PROPOSAL_GENERATION"))
                .andExpect(jsonPath("$.analysisId").value(analysisId.toString()))
                .andExpect(jsonPath("$.context.facts").isArray())
                .andRespond(withStatus(HttpStatus.ACCEPTED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "correlationId": "%s",
                                  "accepted": true,
                                  "externalJobId": "job-42",
                                  "acceptedAt": "%s"
                                }
                                """.formatted(correlationId, acceptedAt)));

        AiTaskSubmissionResponse response = client.submit(request);

        assertTrue(response.accepted());
        assertEquals(correlationId, response.correlationId());
        assertEquals("job-42", response.externalJobId());
        assertEquals(acceptedAt, response.acceptedAt());
        server.verify();
    }

    @Test
    void shouldRejectMismatchedCorrelationIdentifier() {
        AiTaskSubmissionRequest request = request(
                UUID.randomUUID(),
                UUID.randomUUID()
        );
        server.expect(requestTo("http://ai-engine.test/api/v1/ai/tasks"))
                .andRespond(withStatus(HttpStatus.ACCEPTED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "correlationId": "%s",
                                  "accepted": true,
                                  "externalJobId": "job-42",
                                  "acceptedAt": "2026-07-21T18:00:00Z"
                                }
                                """.formatted(UUID.randomUUID())));

        AIEngineCommunicationException exception = assertThrows(
                AIEngineCommunicationException.class,
                () -> client.submit(request)
        );

        assertTrue(exception.getMessage().contains("correlation"));
        server.verify();
    }

    @Test
    void shouldTranslateHttpFailure() {
        AiTaskSubmissionRequest request = request(
                UUID.randomUUID(),
                UUID.randomUUID()
        );
        server.expect(requestTo("http://ai-engine.test/api/v1/ai/tasks"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        AIEngineCommunicationException exception = assertThrows(
                AIEngineCommunicationException.class,
                () -> client.submit(request)
        );

        assertEquals("AI Engine task submission failed", exception.getMessage());
        assertNotNull(exception.getCause());
        server.verify();
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
