package com.hopeful117.devlogai.ai.engine.client;

import com.hopeful117.devlogai.ai.engine.dto.PromptRequest;
import com.hopeful117.devlogai.ai.engine.dto.AiTaskSubmissionResponse;
import com.hopeful117.devlogai.ai.engine.exception.AIEngineCommunicationException;
import com.hopeful117.devlogai.ai.engine.dto.DeliverableGenerationRequest;
import com.hopeful117.devlogai.ai.engine.dto.DeliverableGenerationResponse;
import com.hopeful117.devlogai.deliverable.entity.DeliverableType;
import com.hopeful117.devlogai.ai.task.entity.AiTaskType;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.model.InsightType;
import com.hopeful117.devlogai.knowledge.selection.SelectedKnowledge;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.ContextProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
    void shouldGenerateDeliverableThroughDedicatedEndpoint() {
        UUID requestId = UUID.randomUUID();
        var request = new DeliverableGenerationRequest(
                requestId, UUID.randomUUID(), null, DeliverableType.PROJECT_DESCRIPTION,
                "Engineers", "Concise", "en", null,
                List.of(new DeliverableGenerationRequest.ValidatedInsightSnapshot(
                        UUID.randomUUID(), UUID.randomUUID(), "ARCHITECTURAL", "INFO", "Core", "Spring Core"))
        );
        server.expect(requestTo("http://ai-engine.test/api/v1/deliverables/generate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.validatedInsights[0].title").value("Core"))
                .andExpect(jsonPath("$.facts").doesNotExist())
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body("""
                        {"title":"Description","content":"Content","promptVersion":"deliverable-generation-prompt-v1",
                         "promptDigest":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                         "provider":"mock","modelIdentifier":"deterministic-v1"}
                        """));
        DeliverableGenerationResponse response = client.generateDeliverable(request);
        assertEquals("Description", response.title());
        server.verify();
    }

    @Test
    void shouldSubmitCompleteContractAndAcceptAcknowledgement() {
        UUID correlationId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        Instant acceptedAt = Instant.parse("2026-07-21T18:00:00Z");
        PromptRequest request = request(correlationId, analysisId);

        server.expect(once(), requestTo("http://ai-engine.test/api/v1/ai/tasks"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.correlationId").value(correlationId.toString()))
                .andExpect(jsonPath("$.taskType").value("INSIGHT_GENERATION"))
                .andExpect(jsonPath("$.analysisId").value(analysisId.toString()))
                .andExpect(jsonPath("$.selectedKnowledge.selectedFacts").isArray())
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
        PromptRequest request = request(
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
        PromptRequest request = request(
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

    private PromptRequest request(
            UUID correlationId,
            UUID analysisId
    ) {
        IntentDefinition intent = new IntentDefinition("describe-project", "v1", "Describe",
                List.of(InsightType.PROJECT_PRESENTATION), List.of("traceable"),
                Map.of("type", "object"), "describe-project-prompt-v1");
        SelectedKnowledge selected = new SelectedKnowledge(
                null, null, null, List.of(), List.of(),
                new SelectedKnowledge.DiagnosticSnapshot(true, false, 0, 0), List.of(),
                new RepositoryContext("repository-context-engine-v1",
                        ContextProfile.PROJECT_STATE,
                        List.of("project-state-v1", "history-v1"),
                        "context-intelligence-v1", List.of("test"),
                        List.of(), Map.of(),
                        new RepositoryContext.ContextBudget(60, 500, 20, 6000),
                        0, 0, 0, false, List.of(), List.of(), "b".repeat(64)),
                new SelectedKnowledge.SelectionMetadata("selection-v1", List.of(), 0, 0,
                        new SelectedKnowledge.KnowledgeBudget(40, 25, 10, 60), "COMPLETE"),
                "a".repeat(64));
        return new PromptRequest(correlationId, correlationId, analysisId, UUID.randomUUID(),
                AiTaskType.INSIGHT_GENERATION, intent, null, selected, intent.outputSchema(), Map.of());
    }
}
