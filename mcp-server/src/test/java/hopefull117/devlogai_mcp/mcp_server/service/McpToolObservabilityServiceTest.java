package hopefull117.devlogai_mcp.mcp_server.service;

import hopefull117.devlogai_mcp.mcp_server.client.McpToolEventIngestClient;
import hopefull117.devlogai_mcp.mcp_server.client.McpToolEventIngestPayload;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class McpToolObservabilityServiceTest {

    private static final String PROJECT_SLUG = "devlog-ai";
    private static final String OPERATION_ID = "op-001";
    private static final String TOOL_NAME = "devlog_story_agent";

    @Test
    void recordCompletion_sendsEventTypeAsMcpToolResponded() {
        McpToolEventIngestClient ingestClient = mock(McpToolEventIngestClient.class);
        McpToolObservabilityService service = serviceWithPrincipal(ingestClient, "Bearer token-1");

        service.recordCompletion(PROJECT_SLUG, OPERATION_ID, TOOL_NAME, Instant.now(), null);

        ArgumentCaptor<McpToolEventIngestPayload> captor = ArgumentCaptor.forClass(McpToolEventIngestPayload.class);
        verify(ingestClient).ingest(captor.capture(), any(), any());
        assertThat(captor.getValue().eventType()).isEqualTo("MCP_TOOL_RESPONDED");
        assertThat(captor.getValue().status()).isEqualTo("RESPONDED");
    }

    @Test
    void recordFailure_sendsEventTypeAsMcpToolFailed() {
        McpToolEventIngestClient ingestClient = mock(McpToolEventIngestClient.class);
        McpToolObservabilityService service = serviceWithPrincipal(ingestClient, "Bearer token-1");

        service.recordFailure(PROJECT_SLUG, OPERATION_ID, TOOL_NAME, Instant.now(), "TOOL_EXECUTION_FAILED");

        ArgumentCaptor<McpToolEventIngestPayload> captor = ArgumentCaptor.forClass(McpToolEventIngestPayload.class);
        verify(ingestClient).ingest(captor.capture(), any(), any());
        assertThat(captor.getValue().eventType()).isEqualTo("MCP_TOOL_FAILED");
        assertThat(captor.getValue().status()).isEqualTo("FAILED");
        assertThat(captor.getValue().reasonCode()).isEqualTo("TOOL_EXECUTION_FAILED");
    }

    @Test
    void recordCompletion_extractsAiTaskIdAsCorrelationId() {
        McpToolEventIngestClient ingestClient = mock(McpToolEventIngestClient.class);
        McpToolObservabilityService service = serviceWithPrincipal(ingestClient, "Bearer token-1");
        String resultJson = "{\"status\":\"COMPLETED\",\"aiTaskId\":\"550e8400-e29b-41d4-a716-446655440000\"}";

        service.recordCompletion(PROJECT_SLUG, OPERATION_ID, TOOL_NAME, Instant.now(), resultJson);

        ArgumentCaptor<McpToolEventIngestPayload> captor = ArgumentCaptor.forClass(McpToolEventIngestPayload.class);
        verify(ingestClient).ingest(captor.capture(), any(), any());
        assertThat(captor.getValue().correlationId()).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
    }

    @Test
    void recordCompletion_setsNullCorrelationIdWhenResultJsonIsInvalid() {
        McpToolEventIngestClient ingestClient = mock(McpToolEventIngestClient.class);
        McpToolObservabilityService service = serviceWithPrincipal(ingestClient, "Bearer token-1");

        service.recordCompletion(PROJECT_SLUG, OPERATION_ID, TOOL_NAME, Instant.now(), "not-json");

        ArgumentCaptor<McpToolEventIngestPayload> captor = ArgumentCaptor.forClass(McpToolEventIngestPayload.class);
        verify(ingestClient).ingest(captor.capture(), any(), any());
        assertThat(captor.getValue().correlationId()).isNull();
    }

    @Test
    void recordCompletion_skipsSendWhenNoPrincipal() {
        McpToolEventIngestClient ingestClient = mock(McpToolEventIngestClient.class);
        McpToolObservabilityService service = new McpToolObservabilityService(
                ingestClient,
                () -> Optional.empty(),
                new ObjectMapper());

        service.recordCompletion(PROJECT_SLUG, OPERATION_ID, TOOL_NAME, Instant.now(), null);

        verify(ingestClient, never()).ingest(any(), any(), any());
    }

    @Test
    void recordCompletion_skipsSendWhenBearerTokenIsBlank() {
        McpToolEventIngestClient ingestClient = mock(McpToolEventIngestClient.class);
        McpToolObservabilityService service = serviceWithPrincipal(ingestClient, "");

        service.recordCompletion(PROJECT_SLUG, OPERATION_ID, TOOL_NAME, Instant.now(), null);

        verify(ingestClient, never()).ingest(any(), any(), any());
    }

    private static McpToolObservabilityService serviceWithPrincipal(McpToolEventIngestClient ingestClient, String bearerToken) {
        McpAuthenticatedPrincipalProvider.McpPrincipal principal =
                new McpAuthenticatedPrincipalProvider.McpPrincipal(
                        "test-id",
                        McpAuthenticatedPrincipalProvider.PrincipalKind.HUMAN,
                        "test",
                        bearerToken,
                        null);
        return new McpToolObservabilityService(ingestClient, () -> Optional.of(principal), new ObjectMapper());
    }
}
