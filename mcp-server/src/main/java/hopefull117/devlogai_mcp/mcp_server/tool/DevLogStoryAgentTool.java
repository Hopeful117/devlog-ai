package hopefull117.devlogai_mcp.mcp_server.tool;

import com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResponse;
import hopefull117.devlogai_mcp.mcp_server.client.DevlogProjectContextClient;
import hopefull117.devlogai_mcp.mcp_server.service.McpToolObservabilityService;
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

import java.time.Instant;
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
    // Keep the synchronous MCP call below the client-side request deadline so
    // a bounded timeout response can still return the aiTaskId for resumption.
    private static final long DEFAULT_POLL_TIMEOUT_MS = 90_000L;
    private static final long MAX_POLL_TIMEOUT_MS = 90_000L;
    private static final long DEFAULT_SUBMISSION_TIMEOUT_MS = 10_000L;
    private static final long MAX_SUBMISSION_TIMEOUT_MS = 30_000L;
    private static final long DEFAULT_POLL_INTERVAL_MS = 2_000L;

    private final DevlogProjectContextClient client;
    private final ObjectMapper objectMapper;
    private final long pollTimeoutMs;
    private final long submissionTimeoutMs;
    private final long pollIntervalMs;
    private final McpAuthenticatedPrincipalProvider principalProvider;
    private final McpToolObservabilityService observability;

    public DevLogStoryAgentTool(DevlogProjectContextClient client, ObjectMapper objectMapper) {
        this(client, objectMapper, DEFAULT_POLL_TIMEOUT_MS, DEFAULT_SUBMISSION_TIMEOUT_MS,
                DEFAULT_POLL_INTERVAL_MS,
                () -> java.util.Optional.empty(), null);
    }

    public DevLogStoryAgentTool(
            DevlogProjectContextClient client,
            ObjectMapper objectMapper,
            long pollTimeoutMs,
            long pollIntervalMs,
            McpAuthenticatedPrincipalProvider principalProvider) {
        this(client, objectMapper, pollTimeoutMs, DEFAULT_SUBMISSION_TIMEOUT_MS,
                pollIntervalMs, principalProvider, null);
    }

    public DevLogStoryAgentTool(
            DevlogProjectContextClient client,
            ObjectMapper objectMapper,
            long pollTimeoutMs,
            long submissionTimeoutMs,
            long pollIntervalMs,
            McpAuthenticatedPrincipalProvider principalProvider) {
        this(client, objectMapper, pollTimeoutMs, submissionTimeoutMs, pollIntervalMs,
                principalProvider, null);
    }

    public DevLogStoryAgentTool(
            DevlogProjectContextClient client,
            ObjectMapper objectMapper,
            long pollTimeoutMs,
            long pollIntervalMs,
            McpAuthenticatedPrincipalProvider principalProvider,
            @Nullable McpToolObservabilityService observability) {
        this(client, objectMapper, pollTimeoutMs, DEFAULT_SUBMISSION_TIMEOUT_MS,
                pollIntervalMs, principalProvider, observability);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DevLogStoryAgentTool(
             DevlogProjectContextClient client,
             ObjectMapper objectMapper,
             @Value("${devlog.story-agent.poll.timeout-ms:90000}") long pollTimeoutMs,
             @Value("${devlog.story-agent.submit.timeout-ms:10000}") long submissionTimeoutMs,
             @Value("${devlog.story-agent.poll.interval-ms:2000}") long pollIntervalMs,
            McpAuthenticatedPrincipalProvider principalProvider,
            @Nullable McpToolObservabilityService observability) {
        if (pollTimeoutMs < 0 || pollTimeoutMs > MAX_POLL_TIMEOUT_MS || pollIntervalMs < 0) {
            throw new IllegalArgumentException("Story Agent polling timeout must be between 0 and 90000 ms");
        }
        if (submissionTimeoutMs < 0 || submissionTimeoutMs > MAX_SUBMISSION_TIMEOUT_MS) {
            throw new IllegalArgumentException("Story Agent submission timeout must be between 0 and 30000 ms");
        }
        this.client = client;
        this.objectMapper = objectMapper;
        this.pollTimeoutMs = pollTimeoutMs;
        this.submissionTimeoutMs = submissionTimeoutMs;
        this.pollIntervalMs = pollIntervalMs;
        this.principalProvider = principalProvider;
        this.observability = observability;
    }

    public String execute(
            String projectSlug,
            @Nullable UUID storyId,
            String intent,
            @Nullable List<String> files,
            @Nullable Map<String, Object> guidance,
            @Nullable String idempotencyKey) {
        requireIdempotencyKey(idempotencyKey);
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
            @McpArg(description = "Caller-provided idempotency key required for safe resumption", required = true) String idempotencyKey,
            McpSyncRequestContext requestContext) {
        requireIdempotencyKey(idempotencyKey);
        var principal = principalProvider.currentPrincipal(requestContext)
                .orElseThrow(() -> ResourceSupport.unauthenticated("Authentication is required"));
        String operationId = observability != null ? observability.newOperationId() : null;
        Instant startedAt = Instant.now();
        try {
            String result = execute(projectSlug, storyId, intent, question, files, guidance, idempotencyKey, principal);
            if (observability != null && operationId != null) {
                observability.recordCompletion(projectSlug, operationId, "devlog_story_agent", startedAt,
                        result, principal);
            }
            return result;
        } catch (RuntimeException e) {
            if (observability != null && operationId != null) {
                observability.recordFailure(projectSlug, operationId, "devlog_story_agent", startedAt,
                        "TOOL_EXECUTION_FAILED", principal);
            }
            throw e;
        }
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
        long operationDeadline = System.nanoTime()
                + Math.max(pollTimeoutMs, submissionTimeoutMs * 2) * 1_000_000L;
        try {
            String effectiveQuestion = question == null || question.isBlank() ? intent : question;
            Map<String, Object> effectiveGuidance = guidance == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(guidance);
            effectiveGuidance.putIfAbsent("focus", effectiveQuestion);
            if (!STORY_CONTEXT_ANALYSIS_INTENT.equals(intent)) {
                effectiveGuidance.put("callerIntent", intent);
            }
            DevlogProjectContextClient.SubmitRequest request =
                    new DevlogProjectContextClient.SubmitRequest(
                            STORY_CONTEXT_ANALYSIS_INTENT, files, effectiveGuidance);
            execution = submitWithBoundedResume(
                    projectSlug, storyId, bearerToken, principal.delegationToken(), idempotencyKey, request,
                    operationDeadline);
            if (execution == null) {
                execution = submissionTimeoutExecution(idempotencyKey, 2);
            }
        } catch (RuntimeException exception) {
            log.error("Story Agent backend submission failed projectSlug={} storyId={} hasQuestion={} fileCount={} hasGuidance={} hasIdempotencyKey={}",
                    projectSlug, storyId, question != null, files == null ? 0 : files.size(),
                    guidance != null && !guidance.isEmpty(), idempotencyKey != null, exception);
            throw exception;
        }
        execution = awaitTerminalExecution(execution, principal, operationDeadline);
        try {
            return objectMapper.writeValueAsString(execution);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize DevLog Story Agent response", exception);
        }
    }

    private Map<String, Object> submitWithBoundedResume(
            String projectSlug,
            @Nullable UUID storyId,
            String bearerToken,
            String delegationToken,
            String idempotencyKey,
            DevlogProjectContextClient.SubmitRequest request,
            long operationDeadline) {
        Map<String, Object> execution = submitOnce(
                projectSlug, storyId, bearerToken, delegationToken, idempotencyKey, request,
                operationDeadline);
        if (execution != null) {
            return execution;
        }

        // The first request may have reached Core before its response was lost.
        // Reuse the same key once so Core's existing idempotency contract can
        // return the original task instead of creating a duplicate.
        return submitOnce(projectSlug, storyId, bearerToken, delegationToken, idempotencyKey, request,
                operationDeadline);
    }

    private Map<String, Object> submitOnce(
            String projectSlug,
            @Nullable UUID storyId,
            String bearerToken,
            String delegationToken,
            String idempotencyKey,
            DevlogProjectContextClient.SubmitRequest request,
            long operationDeadline) {
        long deadline = Math.min(operationDeadline,
                System.nanoTime() + submissionTimeoutMs * 1_000_000L);
        try {
            return callUntil(deadline, () -> client.submitStoryContextAgentTask(
                    projectSlug, storyId, bearerToken, idempotencyKey, delegationToken, request));
        } catch (DeadlineExceededException exception) {
            return null;
        }
    }

    private Map<String, Object> submissionTimeoutExecution(String idempotencyKey, int attempts) {
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("status", "TIMED_OUT");
        diagnostics.put("code", "SUBMISSION_TIMEOUT");
        diagnostics.put("lastKnownStatus", "SUBMISSION_IN_PROGRESS");
        diagnostics.put("attempts", attempts);
        diagnostics.put("resumeRequired", true);
        diagnostics.put("resumeAction", "RETRY_WITH_SAME_IDEMPOTENCY_KEY");
        diagnostics.put("idempotencyKeyHash", digest(idempotencyKey));
        return new LinkedHashMap<>(Map.of(
                "status", "TIMED_OUT",
                "diagnostics", diagnostics));
    }

    private String digest(String value) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte current : bytes) {
                result.append(String.format("%02x", current));
            }
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw ResourceSupport.invalidParams(
                    "Idempotency key is required for safe Story Agent resumption");
        }
    }

    private Map<String, Object> awaitTerminalExecution(
            Map<String, Object> initialExecution,
            McpAuthenticatedPrincipalProvider.McpPrincipal principal,
            long operationDeadline) {
        String bearerToken = principal.bearerToken();
        String initialStatus = stringValue(initialExecution.get("status"));
        if (isTerminal(initialStatus)) {
            if ("COMPLETED".equals(initialStatus)) {
                UUID taskId = executionId(initialExecution);
                try {
                    StoryContextAnalysisResponse analysis = callUntil(
                            operationDeadline,
                            () -> client.getStoryContextAgentResultAuthenticated(
                                    taskId, bearerToken, principal.delegationToken()));
                    return completedExecution(initialExecution, initialExecution, analysis);
                } catch (DeadlineExceededException exception) {
                    return completedWithoutResult(initialExecution, taskId, exception);
                } catch (RuntimeException exception) {
                    log.warn("Initially completed Story Agent task {} has no readable result", taskId, exception);
                    return completedWithoutResult(initialExecution, taskId, exception);
                }
            }
            if (("FAILED".equals(initialStatus) || "NOT_ESTABLISHED".equals(initialStatus))
                    && !(initialExecution.get("diagnostics") instanceof Map<?, ?>)) {
                UUID taskId = executionId(initialExecution);
                try {
                    Map<String, Object> taskStatus = callUntil(
                            operationDeadline,
                            () -> client.getStoryContextAgentSnapshot(taskId, principal.principalId(),
                                    principal.kind().name(), principal.authenticationSource(), bearerToken,
                                    principal.delegationToken()));
                    return executionWithStatus(initialExecution, taskStatus);
                } catch (RuntimeException ignored) {
                    // Preserve the original terminal response when the diagnostic read is unavailable.
                }
            }
            return initialExecution;
        }

        UUID taskId = executionId(initialExecution);
        long deadline = Math.min(operationDeadline,
                System.nanoTime() + pollTimeoutMs * 1_000_000L);
        Map<String, Object> latestExecution = new LinkedHashMap<>(initialExecution);

        while (System.nanoTime() < deadline) {
            if (!sleepUntilNextPoll(deadline)) {
                break;
            }

            Map<String, Object> taskStatus;
            try {
                taskStatus = callUntil(deadline,
                         () -> client.getStoryContextAgentSnapshot(taskId, principal.principalId(),
                                 principal.kind().name(), principal.authenticationSource(), bearerToken,
                                 principal.delegationToken()));
            } catch (DeadlineExceededException exception) {
                break;
            }
            latestExecution = executionWithStatus(latestExecution, taskStatus);
            String taskStatusValue = stringValue(taskStatus.get("status"));
            if ("FAILED".equals(taskStatusValue)
                    || "NOT_ESTABLISHED".equals(taskStatusValue)
                    || "TIMED_OUT".equals(taskStatusValue)) {
                return latestExecution;
            }
            if ("COMPLETED".equals(taskStatusValue)) {
                try {
                            StoryContextAnalysisResponse analysis = callUntil(deadline,
                                    () -> client.getStoryContextAgentResultAuthenticated(taskId, bearerToken,
                                            principal.delegationToken()));
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
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        if (latestExecution.get("diagnostics") instanceof Map<?, ?> existing) {
            existing.forEach((key, value) -> diagnostics.put(String.valueOf(key), value));
        }
        diagnostics.put("status", "TIMED_OUT");
        diagnostics.put("code", "POLL_TIMEOUT");
        diagnostics.put("lastKnownStatus", lastKnownStatus);
        diagnostics.put("aiTaskId", taskId.toString());
        latestExecution.put("diagnostics", diagnostics);
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
        incomplete.put("status", "FAILED");
        incomplete.put("diagnostics", Map.of(
                "status", "FAILED",
                "code", "COMPLETED_RESULT_UNAVAILABLE",
                "message", "The agent completed, but its structured result could not be read.",
                "aiTaskId", taskId.toString(),
                "cause", exception.getClass().getSimpleName(),
                "underlyingStatus", "COMPLETED"));
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
        if (taskStatus.get("deadlineAt") != null) {
            diagnostics.put("deadlineAt", taskStatus.get("deadlineAt"));
        }
        if (taskStatus.get("terminalReason") != null) {
            diagnostics.put("terminalReason", taskStatus.get("terminalReason"));
        }
        if (taskStatus.get("recoveryAction") != null) {
            diagnostics.put("recoveryAction", taskStatus.get("recoveryAction"));
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
        return "COMPLETED".equals(status)
                || "FAILED".equals(status)
                || "NOT_ESTABLISHED".equals(status)
                || "TIMED_OUT".equals(status);
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }
}
