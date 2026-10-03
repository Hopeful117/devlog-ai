package hopefull117.devlogai_mcp.mcp_server.tool;

import com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResponse;
import hopefull117.devlogai_mcp.mcp_server.client.DevlogProjectContextClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Thin MCP adapter for the bounded, read-only DevLog Story Agent capability. */
@Component
@Slf4j
public class DevLogStoryAgentTool {
    private static final long DEFAULT_POLL_TIMEOUT_MS = 120_000L;
    private static final long DEFAULT_POLL_INTERVAL_MS = 2_000L;

    private final DevlogProjectContextClient client;
    private final ObjectMapper objectMapper;
    private final long pollTimeoutMs;
    private final long pollIntervalMs;

    public DevLogStoryAgentTool(DevlogProjectContextClient client, ObjectMapper objectMapper) {
        this(client, objectMapper, DEFAULT_POLL_TIMEOUT_MS, DEFAULT_POLL_INTERVAL_MS);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DevLogStoryAgentTool(
            DevlogProjectContextClient client,
            ObjectMapper objectMapper,
            @Value("${devlog.story-agent.poll.timeout-ms:120000}") long pollTimeoutMs,
            @Value("${devlog.story-agent.poll.interval-ms:2000}") long pollIntervalMs) {
        if (pollTimeoutMs < 0 || pollIntervalMs < 0) {
            throw new IllegalArgumentException("Story Agent polling bounds must be non-negative");
        }
        this.client = client;
        this.objectMapper = objectMapper;
        this.pollTimeoutMs = pollTimeoutMs;
        this.pollIntervalMs = pollIntervalMs;
    }

    @McpTool(
            name = "devlog_story_agent",
            description = "Runs one bounded, read-only DevLog Story Agent execution and returns its structured result or execution diagnostics.")
    public String execute(
            @McpArg(description = "DevLog project slug", required = true) String projectSlug,
            @McpArg(description = "Optional Engineering Story UUID", required = false) @Nullable UUID storyId,
            @McpArg(description = "Natural-language investigation objective", required = true) String intent,
            @McpArg(description = "Optional repository file paths", required = false) @Nullable List<String> files,
            @McpArg(description = "Optional human guidance", required = false) @Nullable Map<String, Object> guidance,
            @McpArg(description = "Optional idempotency key", required = false) @Nullable String idempotencyKey) {
        Map<String, Object> execution = client.executeStoryAgent(
                projectSlug,
                idempotencyKey,
                new DevlogProjectContextClient.StoryAgentRequest(storyId, intent, files, guidance));
        execution = awaitTerminalExecution(execution);
        try {
            return objectMapper.writeValueAsString(execution);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize DevLog Story Agent response", exception);
        }
    }

    private Map<String, Object> awaitTerminalExecution(Map<String, Object> initialExecution) {
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

            DevlogProjectContextClient.AiTaskStatusResponse taskStatus = client.getAiTaskStatus(taskId);
            latestExecution = executionWithStatus(latestExecution, taskStatus);
            if ("FAILED".equals(taskStatus.status())) {
                return latestExecution;
            }
            if ("COMPLETED".equals(taskStatus.status())) {
                try {
                    return completedExecution(latestExecution, taskStatus,
                            client.getStoryContextAnalysis(taskId));
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
                "lastKnownStatus", lastKnownStatus,
                "aiTaskId", taskId.toString()));
        return latestExecution;
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
            DevlogProjectContextClient.AiTaskStatusResponse taskStatus) {
        Map<String, Object> execution = new LinkedHashMap<>(previous);
        execution.put("status", taskStatus.status());
        execution.put("aiTaskId", taskStatus.id());
        execution.put("snapshotId", taskStatus.id());
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("status", taskStatus.status());
        if (taskStatus.failureCode() != null) {
            diagnostics.put("failureCode", taskStatus.failureCode());
        }
        if (taskStatus.failureMessage() != null) {
            diagnostics.put("failureMessage", taskStatus.failureMessage());
        }
        execution.put("diagnostics", diagnostics);
        return execution;
    }

    private Map<String, Object> completedExecution(
            Map<String, Object> execution,
            DevlogProjectContextClient.AiTaskStatusResponse taskStatus,
            StoryContextAnalysisResponse analysis) {
        Map<String, Object> completed = new LinkedHashMap<>(execution);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("analysis", analysis);
        if (taskStatus.contextDigest() != null) {
            result.put("contextDigest", taskStatus.contextDigest());
        }
        if (taskStatus.projectionDigest() != null) {
            result.put("projectionDigest", taskStatus.projectionDigest());
        }
        putIfPresent(result, taskStatus.contextSnapshot(), "scope");
        putIfPresent(result, taskStatus.contextSnapshot(), "freshness");
        putIfPresent(result, taskStatus.contextSnapshot(), "accounting");
        completed.put("status", "COMPLETED");
        completed.put("result", result);
        completed.put("diagnostics", Map.of("status", "COMPLETED"));
        return completed;
    }

    private void putIfPresent(Map<String, Object> target, Map<String, Object> source, String key) {
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
