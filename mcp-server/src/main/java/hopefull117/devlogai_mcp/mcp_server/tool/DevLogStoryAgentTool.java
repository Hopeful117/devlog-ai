package hopefull117.devlogai_mcp.mcp_server.tool;

import com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResponse;
import hopefull117.devlogai_mcp.mcp_server.client.DevlogProjectContextClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import hopefull117.devlogai_mcp.mcp_server.service.McpAuthenticatedPrincipalProvider;
import hopefull117.devlogai_mcp.mcp_server.resource.ResourceSupport;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/** Thin MCP adapter for the bounded, read-only DevLog Story Agent capability. */
@Component
@Slf4j
public class DevLogStoryAgentTool {
    private static final String STORY_CONTEXT_ANALYSIS_INTENT = "engineering-story-context-analysis";
    private static final long DEFAULT_POLL_TIMEOUT_MS = 29_000L;
    private static final long MAX_POLL_TIMEOUT_MS = 90_000L;
    private static final long DEFAULT_POLL_INTERVAL_MS = 2_000L;

    private final DevlogProjectContextClient client;
    private final ObjectMapper objectMapper;
    private final long pollTimeoutMs;
    private final long pollIntervalMs;
    private final McpAuthenticatedPrincipalProvider principalProvider;

    public DevLogStoryAgentTool(DevlogProjectContextClient client, ObjectMapper objectMapper) {
        this(client, objectMapper, DEFAULT_POLL_TIMEOUT_MS, DEFAULT_POLL_INTERVAL_MS, () -> java.util.Optional.empty());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DevLogStoryAgentTool(
            DevlogProjectContextClient client,
            ObjectMapper objectMapper,
            @Value("${devlog.story-agent.poll.timeout-ms:29000}") long pollTimeoutMs,
            @Value("${devlog.story-agent.poll.interval-ms:2000}") long pollIntervalMs,
            McpAuthenticatedPrincipalProvider principalProvider) {
        if (pollTimeoutMs < 0 || pollTimeoutMs > MAX_POLL_TIMEOUT_MS || pollIntervalMs < 0) {
            throw new IllegalArgumentException("Story Agent polling timeout must be between 0 and 90000 ms");
        }
        this.client = client;
        this.objectMapper = objectMapper;
        this.pollTimeoutMs = pollTimeoutMs;
        this.pollIntervalMs = pollIntervalMs;
        this.principalProvider = principalProvider;
    }

    public String execute(
            String projectSlug,
            @Nullable UUID storyId,
            String intent,
            @Nullable List<String> files,
            @Nullable Map<String, Object> guidance,
            @Nullable String idempotencyKey) {
        var principal = principalProvider.currentPrincipal()
                .orElseThrow(() -> ResourceSupport.unauthenticated("Authentication is required"));
        return execute(projectSlug, storyId, intent, null, files, guidance, idempotencyKey, principal);
    }

    @McpTool(
            name = "devlog_story_agent",
            description = "Runs one bounded, read-only DevLog Story Agent execution through the canonical Story Context Agent v1 protocol and returns its structured result or execution diagnostics.")
    public String execute(
            @McpArg(description = "DevLog project slug", required = true) String projectSlug,
            @McpArg(description = "Optional Engineering Story UUID", required = false) @Nullable UUID storyId,
            @McpArg(description = "Natural-language investigation objective", required = true) String intent,
            @McpArg(description = "Optional specific question; intent is used when omitted", required = false)
            @Nullable String question,
            @McpArg(description = "Optional repository file paths", required = false) @Nullable List<String> files,
            @McpArg(description = "Optional human guidance", required = false) @Nullable Map<String, Object> guidance,
            @McpArg(description = "Optional idempotency key", required = false) @Nullable String idempotencyKey,
            McpSyncRequestContext requestContext) {
        var principal = principalProvider.currentPrincipal(requestContext)
                .orElseThrow(() -> ResourceSupport.unauthenticated("Authentication is required"));
        return execute(projectSlug, storyId, intent, question, files, guidance, idempotencyKey,
                principal);
    }

    private String execute(
            String projectSlug,
            @Nullable UUID storyId,
            String intent,
            @Nullable String question,
            @Nullable List<String> files,
            @Nullable Map<String, Object> guidance,
            @Nullable String idempotencyKey,
            McpAuthenticatedPrincipalProvider.McpPrincipal principal) {
        String bearerToken = principal.bearerToken();
        if (bearerToken == null || bearerToken.isBlank()) {
            throw ResourceSupport.unauthenticated("Authenticated bearer token is required");
        }
        Map<String, Object> execution;
        try {
            String effectiveQuestion = question == null || question.isBlank() ? intent : question;
            Map<String, Object> effectiveGuidance = guidance == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(guidance);
            effectiveGuidance.putIfAbsent("focus", effectiveQuestion);
            if (!STORY_CONTEXT_ANALYSIS_INTENT.equals(intent)) {
                effectiveGuidance.put("callerIntent", intent);
            }
            execution = client.submitStoryContextAgentTask(
                    projectSlug,
                    storyId,
                    bearerToken,
                    idempotencyKey,
                    new DevlogProjectContextClient.SubmitRequest(
                            STORY_CONTEXT_ANALYSIS_INTENT, files, effectiveGuidance));
        } catch (RuntimeException exception) {
            log.error("Story Agent backend submission failed projectSlug={} storyId={} hasQuestion={} fileCount={} hasGuidance={} hasIdempotencyKey={}",
                    projectSlug, storyId, question != null, files == null ? 0 : files.size(),
                    guidance != null && !guidance.isEmpty(), idempotencyKey != null, exception);
            throw exception;
        }
        execution = awaitTerminalExecution(execution, principal);
        try {
            return objectMapper.writeValueAsString(execution);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize DevLog Story Agent response", exception);
        }
    }

    private Map<String, Object> awaitTerminalExecution(
            Map<String, Object> initialExecution,
            McpAuthenticatedPrincipalProvider.McpPrincipal principal) {
        String bearerToken = principal.bearerToken();
        String initialStatus = stringValue(initialExecution.get("status"));
        if (isTerminal(initialStatus)) {
            return initialExecution;
        }

        UUID taskId = executionId(initialExecution);
        long deadline = System.nanoTime() + pollTimeoutMs * 1_000_000L;
        Map<String, Object> latestExecution = new LinkedHashMap<>(initialExecution);

        while (System.nanoTime() < deadline) {
            if (!sleepUntilNextPoll(deadline)) {
                break;
            }

            Map<String, Object> taskStatus;
            try {
                taskStatus = callUntil(deadline,
                        () -> client.getStoryContextAgentSnapshot(taskId, principal.principalId(),
                                principal.kind().name(), principal.authenticationSource(), bearerToken));
            } catch (DeadlineExceededException exception) {
                break;
            }
            latestExecution = executionWithStatus(latestExecution, taskStatus);
            if ("FAILED".equals(stringValue(taskStatus.get("status")))) {
                return latestExecution;
            }
            if ("COMPLETED".equals(stringValue(taskStatus.get("status")))) {
                try {
                    StoryContextAnalysisResponse analysis = callUntil(deadline,
                            () -> client.getStoryContextAgentResultAuthenticated(taskId, bearerToken));
                    return completedExecution(latestExecution, taskStatus,
                            analysis);
                } catch (DeadlineExceededException exception) {
                    break;
                } catch (RuntimeException exception) {
                    log.warn("Completed Story Agent task {} has no readable result", taskId, exception);
                    return completedWithoutResult(latestExecution, taskId, exception);
                }
            }
        }

        String lastKnownStatus = stringValue(latestExecution.get("status"));
        latestExecution.put("status", "TIMED_OUT");
        latestExecution.put("diagnostics", Map.of(
                "status", "TIMED_OUT",
                "code", "POLL_TIMEOUT",
                "lastKnownStatus", lastKnownStatus,
                "aiTaskId", taskId.toString()));
        return latestExecution;
    }

    private <T> T callUntil(long deadline, Supplier<T> operation) {
        long remainingNanos = deadline - System.nanoTime();
        if (remainingNanos <= 0) {
            throw new DeadlineExceededException();
        }

        FutureTask<T> task = new FutureTask<>(operation::get);
        Thread worker = Thread.ofVirtual().start(task);
        try {
            return task.get(remainingNanos, TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            task.cancel(true);
            worker.interrupt();
            throw new DeadlineExceededException();
        } catch (InterruptedException exception) {
            task.cancel(true);
            worker.interrupt();
            Thread.currentThread().interrupt();
            throw new DeadlineExceededException();
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Story Agent polling failed", cause);
        }
    }

    private static final class DeadlineExceededException extends RuntimeException {
    }

    private Map<String, Object> completedWithoutResult(
            Map<String, Object> execution,
            UUID taskId,
            RuntimeException exception) {
        Map<String, Object> incomplete = new LinkedHashMap<>(execution);
        incomplete.put("status", "COMPLETED");
        incomplete.put("diagnostics", Map.of(
                "status", "COMPLETED",
                "code", "COMPLETED_RESULT_UNAVAILABLE",
                "message", "The agent completed, but its structured result could not be read.",
                "aiTaskId", taskId.toString(),
                "cause", exception.getClass().getSimpleName()));
        return incomplete;
    }

    private boolean sleepUntilNextPoll(long deadline) {
        long remainingMs = (deadline - System.nanoTime()) / 1_000_000L;
        if (remainingMs <= 0) {
            return false;
        }
        long sleepMs = Math.min(pollIntervalMs, remainingMs);
        try {
            Thread.sleep(sleepMs);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private Map<String, Object> executionWithStatus(
            Map<String, Object> previous,
            Map<String, Object> taskStatus) {
        Map<String, Object> execution = new LinkedHashMap<>(previous);
        String status = stringValue(taskStatus.get("status"));
        Object id = taskStatus.get("id");
        execution.put("status", status);
        execution.put("aiTaskId", id);
        execution.put("snapshotId", id);
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("status", status);
        if (taskStatus.get("failureCode") != null) {
            diagnostics.put("failureCode", taskStatus.get("failureCode"));
        }
        if (taskStatus.get("failureMessage") != null) {
            diagnostics.put("failureMessage", taskStatus.get("failureMessage"));
        }
        execution.put("diagnostics", diagnostics);
        return execution;
    }

    private Map<String, Object> completedExecution(
            Map<String, Object> execution,
            Map<String, Object> taskStatus,
            StoryContextAnalysisResponse analysis) {
        Map<String, Object> completed = new LinkedHashMap<>(execution);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("analysis", analysis);
        if (taskStatus.get("contextDigest") != null) {
            result.put("contextDigest", taskStatus.get("contextDigest"));
        }
        if (taskStatus.get("projectionDigest") != null) {
            result.put("projectionDigest", taskStatus.get("projectionDigest"));
        }
        if (taskStatus.get("contextSnapshot") instanceof Map<?, ?> contextSnapshot) {
            putIfPresent(result, contextSnapshot, "scope");
            putIfPresent(result, contextSnapshot, "freshness");
            putIfPresent(result, contextSnapshot, "accounting");
        }
        completed.put("status", "COMPLETED");
        completed.put("result", result);
        completed.put("diagnostics", Map.of("status", "COMPLETED"));
        return completed;
    }

    private void putIfPresent(Map<String, Object> target, Map<?, ?> source, String key) {
        if (source != null && source.get(key) != null) {
            target.put(key, source.get(key));
        }
    }

    private UUID executionId(Map<String, Object> execution) {
        Object value = execution.get("aiTaskId");
        if (value == null) {
            value = execution.get("snapshotId");
        }
        if (value == null) {
            throw new IllegalStateException("Story Agent response has no execution identity");
        }
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Story Agent response has an invalid execution identity", exception);
        }
    }

    private boolean isTerminal(String status) {
        return "COMPLETED".equals(status) || "FAILED".equals(status);
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }
}
