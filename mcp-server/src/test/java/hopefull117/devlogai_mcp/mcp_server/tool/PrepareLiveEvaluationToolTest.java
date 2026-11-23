package hopefull117.devlogai_mcp.mcp_server.tool;

import hopefull117.devlogai_mcp.mcp_server.client.DevlogProjectContextClient;
import hopefull117.devlogai_mcp.mcp_server.service.McpAuthenticatedPrincipalProvider;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PrepareLiveEvaluationToolTest {
    @Test
    void forwardsBoundedPreparationToCoreWithoutExecutingProvider() {
        DevlogProjectContextClient client = mock(DevlogProjectContextClient.class);
        when(client.prepareLiveEvaluation(
                eq("devlog-ai"), eq("Bearer test"), eq("delegation"), eq("q1-prepare-1"), any()))
                .thenReturn(Map.of("status", "BLOCKED", "providerCalls", 0, "networkCalls", 0));
        var principal = (McpAuthenticatedPrincipalProvider) () -> Optional.of(
                new McpAuthenticatedPrincipalProvider.McpPrincipal(
                        "agent-1", McpAuthenticatedPrincipalProvider.PrincipalKind.AI_AGENT,
                        "test", "Bearer test", "delegation"));
        PrepareLiveEvaluationTool tool = new PrepareLiveEvaluationTool(client, new ObjectMapper(), principal);

        String result = tool.prepare(
                "devlog-ai", "q1-prepare-1", "q1", "1", "Q1", "manifest", "1", "a".repeat(64),
                "ACCEPTED", "b".repeat(64), "c".repeat(40), "d".repeat(64), Map.of("reproducible", true),
                Map.of(), Map.of("maxObservations", 3), "openai_chatgpt", "gpt-5.6-luna",
                Map.of("status", "APPROVED"), "q1/run", List.of("observation"), null);

        assertTrue(result.contains("BLOCKED"));
        verify(client).prepareLiveEvaluation(eq("devlog-ai"), eq("Bearer test"), eq("delegation"),
                eq("q1-prepare-1"), any());
        verifyNoMoreInteractions(client);
    }
}
