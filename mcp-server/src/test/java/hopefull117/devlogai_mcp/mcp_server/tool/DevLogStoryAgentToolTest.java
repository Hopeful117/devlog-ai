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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    void retriesATimedOutSubmissionWithTheSameIdempotencyKey() throws Exception {
        DevlogProjectContextClient client = mock(DevlogProjectContextClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        DevLogStoryAgentTool tool = new DevLogStoryAgentTool(
                client, objectMapper, 0, 50, 0, principalProvider());
        UUID taskId = UUID.randomUUID();
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch firstAttemptStarted = new CountDownLatch(1);
        Map<String, Object> submitted = Map.of(
                "status", "SUBMITTED", "aiTaskId", taskId, "snapshotId", taskId);

        when(client.submitStoryContextAgentTask(any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    if (attempts.incrementAndGet() == 1) {
                        firstAttemptStarted.countDown();
                        Thread.sleep(TimeUnit.SECONDS.toMillis(10));
                    }
                    return submitted;
                });

        JsonNode json = objectMapper.readTree(
                tool.execute("devlog-ai", null, "intent", null, null, "request-1"));

        assertThat(firstAttemptStarted.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(json.path("diagnostics").path("code").asText()).isEqualTo("POLL_TIMEOUT");
        assertThat(json.path("aiTaskId").asText()).isEqualTo(taskId.toString());
        assertThat(attempts).hasValue(2);
        verify(client, org.mockito.Mockito.times(2)).submitStoryContextAgentTask(
                eq("devlog-ai"), eq((UUID) null), eq("Bearer test"), eq("request-1"),
                eq(""), any(DevlogProjectContextClient.SubmitRequest.class));
    }

    @Test
    void returnsSubmissionTimeoutWhenSameKeyRecoveryAlsoTimesOut() throws Exception {
        DevlogProjectContextClient client = mock(DevlogProjectContextClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        DevLogStoryAgentTool tool = new DevLogStoryAgentTool(
                client, objectMapper, 100, 20, 0, principalProvider());
        AtomicInteger attempts = new AtomicInteger();

        when(client.submitStoryContextAgentTask(any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    attempts.incrementAndGet();
                    Thread.sleep(TimeUnit.SECONDS.toMillis(10));
                    return Map.of();
                });

        long startedAt = System.nanoTime();
        JsonNode json = objectMapper.readTree(
                tool.execute("devlog-ai", null, "intent with secret payload", null, null, "request-1"));
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        assertThat(json.path("status").asText()).isEqualTo("TIMED_OUT");
        assertThat(json.path("diagnostics").path("code").asText()).isEqualTo("SUBMISSION_TIMEOUT");
        assertThat(json.path("diagnostics").path("resumeRequired").asBoolean()).isTrue();
        assertThat(json.path("diagnostics").path("resumeAction").asText())
                .isEqualTo("RETRY_WITH_SAME_IDEMPOTENCY_KEY");
        assertThat(attempts).hasValue(2);
        assertThat(elapsedMs).isLessThan(500);
        assertThat(json.toString()).doesNotContain("Bearer test", "intent with secret payload");
        verify(client, never()).getStoryContextAgentSnapshot(any(), any(), any(), any(), any(), any());
    }

    @Test
    void concurrentEquivalentResumesObserveOneTaskIdentity() throws Exception {
        DevlogProjectContextClient client = mock(DevlogProjectContextClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        DevLogStoryAgentTool tool = new DevLogStoryAgentTool(
                client, objectMapper, 0, 1_000, 0, principalProvider());
        UUID taskId = UUID.randomUUID();
        CountDownLatch submissionsStarted = new CountDownLatch(2);
        Map<String, Object> submitted = Map.of(
                "status", "SUBMITTED", "aiTaskId", taskId, "snapshotId", taskId);

        when(client.submitStoryContextAgentTask(any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    submissionsStarted.countDown();
                    assertThat(submissionsStarted.await(1, TimeUnit.SECONDS)).isTrue();
                    return submitted;
                });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(
                    () -> tool.execute("devlog-ai", null, "intent", null, null, "same-key"));
            Future<String> second = executor.submit(
                    () -> tool.execute("devlog-ai", null, "intent", null, null, "same-key"));

            JsonNode firstJson = objectMapper.readTree(first.get(2, TimeUnit.SECONDS));
            JsonNode secondJson = objectMapper.readTree(second.get(2, TimeUnit.SECONDS));
            assertThat(firstJson.path("aiTaskId").asText()).isEqualTo(taskId.toString());
            assertThat(secondJson.path("aiTaskId").asText()).isEqualTo(taskId.toString());
            verify(client, org.mockito.Mockito.times(2)).submitStoryContextAgentTask(
                    eq("devlog-ai"), eq((UUID) null), eq("Bearer test"), eq("same-key"),
                    eq(""), any(DevlogProjectContextClient.SubmitRequest.class));
        } finally {
            executor.shutdownNow();
        }
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
                () -> new DevLogStoryAgentTool(client, new ObjectMapper(), 180_001, 1_000, principalProvider()));
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

    @Test
    void recordsFailureEventWhenSubmissionThrows() {
        DevlogProjectContextClient client = mock(DevlogProjectContextClient.class);
        hopefull117.devlogai_mcp.mcp_server.service.McpToolObservabilityService observability =
                mock(hopefull117.devlogai_mcp.mcp_server.service.McpToolObservabilityService.class);
        when(observability.newOperationId()).thenReturn("obs-op-001");
        DevLogStoryAgentTool tool = new DevLogStoryAgentTool(
                client, new ObjectMapper(), 25_000, 2_000, principalProvider(), observability);

        when(client.submitStoryContextAgentTask(any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("backend unreachable"));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () ->
                tool.execute("devlog-ai", null, "intent", null, null, null, "request-1", (org.springframework.ai.mcp.annotation.context.McpSyncRequestContext) null));

        verify(observability).recordFailure(
                eq("devlog-ai"),
                eq("obs-op-001"),
                eq("devlog_story_agent"),
                any(),
                eq("TOOL_EXECUTION_FAILED"),
                any(McpAuthenticatedPrincipalProvider.McpPrincipal.class));
        verify(observability, never()).recordCompletion(any(), any(), any(), any(), any());
    }

    @Test
    void usesRequestScopedPrincipalForBackendAndObservability() {
        DevlogProjectContextClient client = mock(DevlogProjectContextClient.class);
        hopefull117.devlogai_mcp.mcp_server.service.McpToolObservabilityService observability =
                mock(hopefull117.devlogai_mcp.mcp_server.service.McpToolObservabilityService.class);
        McpAuthenticatedPrincipalProvider.McpPrincipal ambient = new McpAuthenticatedPrincipalProvider.McpPrincipal(
                "ambient", McpAuthenticatedPrincipalProvider.PrincipalKind.SYSTEM,
                "ambient", "Bearer ambient", "delegation-ambient");
        McpAuthenticatedPrincipalProvider.McpPrincipal request = new McpAuthenticatedPrincipalProvider.McpPrincipal(
                "request", McpAuthenticatedPrincipalProvider.PrincipalKind.HUMAN,
                "request", "Bearer request", "delegation-request");
        McpAuthenticatedPrincipalProvider principals = new McpAuthenticatedPrincipalProvider() {
            @Override
            public java.util.Optional<McpPrincipal> currentPrincipal() {
                return java.util.Optional.of(ambient);
            }

            @Override
            public java.util.Optional<McpPrincipal> currentPrincipal(
                    org.springframework.ai.mcp.annotation.context.McpSyncRequestContext requestContext) {
                return java.util.Optional.of(request);
            }
        };
        DevLogStoryAgentTool tool = new DevLogStoryAgentTool(
                client, new ObjectMapper(), 25_000, 2_000, principals, observability);
        when(observability.newOperationId()).thenReturn("obs-op-request");
        when(client.submitStoryContextAgentTask(any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("backend unreachable"));

        org.springframework.ai.mcp.annotation.context.McpSyncRequestContext requestContext =
                mock(org.springframework.ai.mcp.annotation.context.McpSyncRequestContext.class);
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () ->
                tool.execute("devlog-ai", null, "intent", null, null, null, "request-1", requestContext));

        verify(client).submitStoryContextAgentTask(
                eq("devlog-ai"), eq((UUID) null), eq("Bearer request"), eq("request-1"),
                 eq("delegation-request"), any());
        verify(observability).recordFailure(
                eq("devlog-ai"), eq("obs-op-request"), eq("devlog_story_agent"),
                any(), eq("TOOL_EXECUTION_FAILED"), eq(request));
    }

    @Test
    void rejectsMissingIdempotencyKeyBeforeBackendSubmission() {
        DevlogProjectContextClient client = mock(DevlogProjectContextClient.class);
        DevLogStoryAgentTool tool = new DevLogStoryAgentTool(
                client, new ObjectMapper(), 25_000, 2_000, principalProvider());

        org.junit.jupiter.api.Assertions.assertThrows(io.modelcontextprotocol.spec.McpError.class, () ->
                tool.execute("devlog-ai", null, "intent", null, null, " "));

        verify(client, never()).submitStoryContextAgentTask(any(), any(), any(), any(), any(), any());
    }

    private static McpAuthenticatedPrincipalProvider principalProvider() {
        return () -> java.util.Optional.of(new McpAuthenticatedPrincipalProvider.McpPrincipal(
                "test-principal", McpAuthenticatedPrincipalProvider.PrincipalKind.HUMAN,
                "test", "Bearer test", ""));
    }
}
