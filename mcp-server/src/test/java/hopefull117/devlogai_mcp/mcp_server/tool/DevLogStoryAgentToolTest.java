package hopefull117.devlogai_mcp.mcp_server.tool;

import hopefull117.devlogai_mcp.mcp_server.client.DevlogProjectContextClient;
import hopefull117.devlogai_mcp.mcp_server.service.McpAuthenticatedPrincipalProvider;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DevLogStoryAgentToolTest {
    @Test
    void forwardsTheHighLevelRequestAndReturnsCoreExecution() throws Exception {
        DevlogProjectContextClient client = mock(DevlogProjectContextClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        DevLogStoryAgentTool tool = new DevLogStoryAgentTool(client, objectMapper,
                25_000, 2_000, principalProvider());
        UUID storyId = UUID.randomUUID();
        Map<String, Object> guidance = Map.of("focus", "grounding");
        Map<String, Object> response = Map.of(
                "status", "PROCESSING",
                "aiTaskId", storyId,
                "snapshotId", storyId,
                "result", Map.of(),
                "diagnostics", Map.of("status", "PROCESSING"));

        when(client.submitStoryContextAgentTask(
                eq("devlog-ai"), eq(storyId), eq("Bearer test"), eq("request-1"),
                 eq(""), eq(new DevlogProjectContextClient.SubmitRequest(
                        "engineering-story-context-analysis", List.of("README.md"), guidance))))
                .thenReturn(response);
        when(client.getStoryContextAgentSnapshot(storyId, "test-principal", "HUMAN", "test", "Bearer test", "")).thenReturn(
                Map.of("id", storyId, "status", "FAILED", "failureCode", "FAILED", "failureMessage", "failure"));

        String result = tool.execute(
                "devlog-ai", storyId, "engineering-story-context-analysis",
                List.of("README.md"), guidance, "request-1");

        JsonNode json = objectMapper.readTree(result);
        assertThat(json.path("status").asString()).isEqualTo("FAILED");
        assertThat(json.path("aiTaskId").asString()).isEqualTo(storyId.toString());
        assertThat(json.path("snapshotId").asString()).isEqualTo(storyId.toString());
        assertThat(json.path("result").isEmpty()).isTrue();
        assertThat(json.path("diagnostics").path("status").asString()).isEqualTo("FAILED");
        verify(client).submitStoryContextAgentTask(
                eq("devlog-ai"), eq(storyId), eq("Bearer test"), eq("request-1"),
                 eq(""), eq(new DevlogProjectContextClient.SubmitRequest(
                        "engineering-story-context-analysis", List.of("README.md"), guidance)));
        verify(client).getStoryContextAgentSnapshot(storyId, "test-principal", "HUMAN", "test", "Bearer test", "");
    }

    @Test
    void forwardsDistinctIntentAndQuestionValues() throws Exception {
        DevlogProjectContextClient client = mock(DevlogProjectContextClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        DevLogStoryAgentTool tool = new DevLogStoryAgentTool(client, objectMapper,
                25_000, 2_000, principalProvider());
        UUID taskId = UUID.randomUUID();
        Map<String, Object> response = Map.of(
                "status", "FAILED",
                "aiTaskId", taskId,
                "snapshotId", taskId,
                "result", Map.of(),
                "diagnostics", Map.of("status", "FAILED"));

        when(client.submitStoryContextAgentTask(
                eq("devlog-ai"), eq((UUID) null), eq("Bearer test"), eq("request-1"),
                 eq(""), eq(new DevlogProjectContextClient.SubmitRequest(
                        "engineering-story-context-analysis", List.of("README.md"),
                        Map.of("focus", "polling", "callerIntent", "Investigate Story Agent")))))
                .thenReturn(response);

        String result = tool.execute(
                "devlog-ai", null, "Investigate Story Agent", "Which evidence explains polling?",
                List.of("README.md"), Map.of("focus", "polling"), "request-1", null);

        assertThat(objectMapper.readTree(result).path("status").asString()).isEqualTo("FAILED");
        verify(client).submitStoryContextAgentTask(
                eq("devlog-ai"), eq((UUID) null), eq("Bearer test"), eq("request-1"),
                eq(""),
                eq(new DevlogProjectContextClient.SubmitRequest(
                        "engineering-story-context-analysis", List.of("README.md"),
                        Map.of("focus", "polling", "callerIntent", "Investigate Story Agent"))));
    }

    @Test
    void pollsUntilCompletionAndReadsThePersistedAnalysis() throws Exception {
        DevlogProjectContextClient client = mock(DevlogProjectContextClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        DevLogStoryAgentTool tool = new DevLogStoryAgentTool(client, objectMapper, 100, 0, principalProvider());
        UUID taskId = UUID.randomUUID();
        when(client.submitStoryContextAgentTask(eq("devlog-ai"), eq((UUID) null), eq("Bearer test"), eq("request-1"),
                eq(""),
                eq(new DevlogProjectContextClient.SubmitRequest(
                        "engineering-story-context-analysis", null, Map.of("focus", "intent", "callerIntent", "intent")))))
                .thenReturn(Map.of("status", "SUBMITTED", "aiTaskId", taskId, "snapshotId", taskId,
                        "result", Map.of(), "diagnostics", Map.of("status", "SUBMITTED")));
        when(client.getStoryContextAgentSnapshot(taskId, "test-principal", "HUMAN", "test", "Bearer test", "")).thenReturn(
                Map.of("id", taskId, "status", "COMPLETED", "contextDigest", "context",
                        "projectionDigest", "projection", "contextSnapshot", Map.of("scope", Map.of("projectSlug", "devlog-ai"))));
        when(client.getStoryContextAgentResultAuthenticated(taskId, "Bearer test", "")).thenReturn(
                new com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResponse(null, Map.of()));

        JsonNode json = objectMapper.readTree(tool.execute("devlog-ai", null, "intent", null, null, "request-1"));

        assertThat(json.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(json.path("result").path("contextDigest").asText()).isEqualTo("context");
        assertThat(json.path("result").path("projectionDigest").asText()).isEqualTo("projection");
        verify(client).getStoryContextAgentSnapshot(taskId, "test-principal", "HUMAN", "test", "Bearer test", "");
        verify(client).getStoryContextAgentResultAuthenticated(taskId, "Bearer test", "");
    }

    @Test
    void preservesNullSnapshotValuesWhilePolling() throws Exception {
        DevlogProjectContextClient client = mock(DevlogProjectContextClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        DevLogStoryAgentTool tool = new DevLogStoryAgentTool(client, objectMapper, 100, 0, principalProvider());
        UUID taskId = UUID.randomUUID();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("storyId", null);
        snapshot.put("scope", Map.of("projectSlug", "trading-os"));

        when(client.submitStoryContextAgentTask(eq("trading-os"), eq((UUID) null), eq("Bearer test"), eq("request-1"),
                 eq(""), eq(new DevlogProjectContextClient.SubmitRequest(
                        "engineering-story-context-analysis", null, Map.of("focus", "intent", "callerIntent", "intent")))))
                .thenReturn(Map.of("status", "SUBMITTED", "aiTaskId", taskId, "snapshotId", taskId));
        when(client.getStoryContextAgentSnapshot(taskId, "test-principal", "HUMAN", "test", "Bearer test", "")).thenReturn(
                Map.of("id", taskId, "status", "COMPLETED", "contextDigest", "context",
                        "projectionDigest", "projection", "contextSnapshot", snapshot));
        when(client.getStoryContextAgentResultAuthenticated(taskId, "Bearer test", "")).thenReturn(
                new com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResponse(null, Map.of()));

        JsonNode json = objectMapper.readTree(
                tool.execute("trading-os", null, "intent", null, null, "request-1"));

        assertThat(json.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(json.path("result").path("scope").path("projectSlug").asText())
                .isEqualTo("trading-os");
        verify(client).getStoryContextAgentSnapshot(taskId, "test-principal", "HUMAN", "test", "Bearer test", "");
        verify(client).getStoryContextAgentResultAuthenticated(taskId, "Bearer test", "");
    }

    @Test
    void returnsBoundedTimeoutWhenTheTaskDoesNotReachATerminalState() throws Exception {
        DevlogProjectContextClient client = mock(DevlogProjectContextClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        DevLogStoryAgentTool tool = new DevLogStoryAgentTool(client, objectMapper, 0, 0, principalProvider());
        UUID taskId = UUID.randomUUID();
        when(client.submitStoryContextAgentTask(eq("devlog-ai"), eq((UUID) null), eq("Bearer test"), eq("request-1"),
                 eq(""), eq(new DevlogProjectContextClient.SubmitRequest(
                        "engineering-story-context-analysis", null, Map.of("focus", "intent", "callerIntent", "intent")))))
                .thenReturn(Map.of("status", "SUBMITTED", "aiTaskId", taskId, "snapshotId", taskId));

        JsonNode json = objectMapper.readTree(tool.execute("devlog-ai", null, "intent", null, null, "request-1"));

        assertThat(json.path("status").asText()).isEqualTo("TIMED_OUT");
        assertThat(json.path("diagnostics").path("code").asText()).isEqualTo("POLL_TIMEOUT");
        assertThat(json.path("diagnostics").path("lastKnownStatus").asText()).isEqualTo("SUBMITTED");
    }

    @Test
    void cancelsAnInFlightStatusCallAtThePollingDeadline() throws Exception {
        DevlogProjectContextClient client = mock(DevlogProjectContextClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        DevLogStoryAgentTool tool = new DevLogStoryAgentTool(client, objectMapper, 50, 0, principalProvider());
        UUID taskId = UUID.randomUUID();
        CountDownLatch statusStarted = new CountDownLatch(1);
        when(client.submitStoryContextAgentTask(eq("devlog-ai"), eq((UUID) null), eq("Bearer test"), eq("request-1"),
                 eq(""), eq(new DevlogProjectContextClient.SubmitRequest(
                        "engineering-story-context-analysis", null, Map.of("focus", "intent", "callerIntent", "intent")))))
                .thenReturn(Map.of("status", "SUBMITTED", "aiTaskId", taskId, "snapshotId", taskId));
        when(client.getStoryContextAgentSnapshot(taskId, "test-principal", "HUMAN", "test", "Bearer test", "")).thenAnswer(invocation -> {
            statusStarted.countDown();
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(10));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return Map.of("id", taskId, "status", "PROCESSING");
        });

        long startedAt = System.nanoTime();
        JsonNode json = objectMapper.readTree(
                tool.execute("devlog-ai", null, "intent", null, null, "request-1"));

        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        assertThat(statusStarted.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(elapsedMs).isLessThan(500);
        assertThat(json.path("status").asText()).isEqualTo("TIMED_OUT");
        assertThat(json.path("diagnostics").path("code").asText()).isEqualTo("POLL_TIMEOUT");
    }

    @Test
    void rejectsPollingTimeoutThatCouldExceedMcpClientTimeout() {
        DevlogProjectContextClient client = mock(DevlogProjectContextClient.class);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new DevLogStoryAgentTool(client, new ObjectMapper(), 90_001, 1_000, principalProvider()));
    }

    @Test
    void exposesACompletedTaskWhenItsStructuredResultCannotBeRead() throws Exception {
        DevlogProjectContextClient client = mock(DevlogProjectContextClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        DevLogStoryAgentTool tool = new DevLogStoryAgentTool(client, objectMapper, 100, 0, principalProvider());
        UUID taskId = UUID.randomUUID();
        when(client.submitStoryContextAgentTask(eq("trading-os"), eq((UUID) null), eq("Bearer test"), eq("request-1"),
                 eq(""), eq(new DevlogProjectContextClient.SubmitRequest(
                        "engineering-story-context-analysis", null, Map.of("focus", "intent", "callerIntent", "intent")))))
                .thenReturn(Map.of("status", "SUBMITTED", "aiTaskId", taskId, "snapshotId", taskId));
        when(client.getStoryContextAgentSnapshot(taskId, "test-principal", "HUMAN", "test", "Bearer test", "")).thenReturn(
                Map.of("id", taskId, "status", "COMPLETED", "contextDigest", "context", "projectionDigest", "projection"));
        when(client.getStoryContextAgentResultAuthenticated(taskId, "Bearer test", ""))
                .thenThrow(new IllegalStateException("analysis endpoint returned no result"));

        JsonNode json = objectMapper.readTree(
                tool.execute("trading-os", null, "intent", null, null, "request-1"));

        assertThat(json.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(json.path("result").isMissingNode()).isTrue();
        assertThat(json.path("diagnostics").path("code").asText())
                .isEqualTo("COMPLETED_RESULT_UNAVAILABLE");
        assertThat(json.path("diagnostics").path("aiTaskId").asText())
                .isEqualTo(taskId.toString());
        verify(client).getStoryContextAgentSnapshot(taskId, "test-principal", "HUMAN", "test", "Bearer test", "");
        verify(client).getStoryContextAgentResultAuthenticated(taskId, "Bearer test", "");
    }

    private static McpAuthenticatedPrincipalProvider principalProvider() {
        return () -> java.util.Optional.of(new McpAuthenticatedPrincipalProvider.McpPrincipal(
                "test-principal", McpAuthenticatedPrincipalProvider.PrincipalKind.HUMAN,
                "test", "Bearer test", ""));
    }
}
