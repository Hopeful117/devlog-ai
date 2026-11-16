package com.hopeful117.devlogai.mcptoolobservability;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentSessionService {
    static final Duration IDLE_THRESHOLD = Duration.ofMinutes(10);
    static final Duration ACTIVE_TOOL_THRESHOLD = Duration.ofMinutes(2);

    private final McpToolEventRepository repository;
    private final McpSessionLifecycleEventRepository lifecycleRepository;

    @Transactional(readOnly = true)
    public AgentSessionPageResponse listByProject(UUID projectId, int page, int size) {
        int clampedPage = Math.max(0, page);
        int clampedSize = Math.clamp(size, 1, 50);
        long total = repository.countSessionsByProject(projectId);
        List<McpToolEventRepository.AgentSessionProjection> rows =
                repository.findSessionsByProject(projectId, clampedPage * clampedSize, clampedSize);
        Instant now = Instant.now();
        return new AgentSessionPageResponse(
                rows.stream().map(r -> toView(r, projectId, now)).toList(),
                total,
                clampedPage,
                clampedSize);
    }

    @Transactional(readOnly = true)
    public McpOperationPageResponse listOperationsBySession(
            UUID projectId, String sessionId, int page, int size) {
        int clampedPage = Math.max(0, page);
        int clampedSize = Math.clamp(size, 1, 50);
        long total = repository.countOperationsByProjectAndSession(projectId, sessionId);
        List<McpToolEventRepository.McpOperationProjection> rows =
                repository.findOperationsByProjectAndSession(
                        projectId, sessionId, clampedPage * clampedSize, clampedSize);
        return new McpOperationPageResponse(
                rows.stream().map(AgentSessionService::toOperationView).toList(),
                total,
                clampedPage,
                clampedSize);
    }

    private AgentSessionView toView(McpToolEventRepository.AgentSessionProjection p,
                                    UUID projectId, Instant now) {
        boolean hasEnded = lifecycleRepository.hasEndedEvent(projectId, p.getAgentSessionId());
        String lifecycleStatus = computeLifecycleStatus(
                hasEnded,
                p.getActiveOperationCount(),
                p.getLastObservedAt(),
                now,
                IDLE_THRESHOLD,
                ACTIVE_TOOL_THRESHOLD);
        return new AgentSessionView(
                p.getAgentSessionId(),
                p.getAgentProvider(),
                p.getFirstObservedAt(),
                p.getLastObservedAt(),
                p.getOperationCount(),
                p.getRespondedCount(),
                p.getFailedCount(),
                p.getInterruptedCount(),
                p.getActiveOperationCount(),
                lifecycleStatus);
    }

    static String computeLifecycleStatus(
            boolean hasEndedEvent,
            long activeOperationCount,
            Instant lastObservedAt,
            Instant now,
            Duration idleThreshold,
            Duration activeToolThreshold) {
        if (hasEndedEvent) return "ENDED";
        if (lastObservedAt == null) return "UNKNOWN";
        Duration sinceLastActivity = Duration.between(lastObservedAt, now);
        if (activeOperationCount > 0 && sinceLastActivity.compareTo(activeToolThreshold) <= 0) {
            return "ACTIVE_TOOL";
        }
        if (sinceLastActivity.compareTo(idleThreshold) > 0) return "IDLE";
        return "UNKNOWN";
    }

    private static McpOperationView toOperationView(McpToolEventRepository.McpOperationProjection p) {
        Integer gen = p.getGeneration();
        return new McpOperationView(
                p.getInstanceId(),
                p.getOperationId(),
                p.getToolName(),
                p.getStatus(),
                gen != null ? gen : 0,
                p.getStartedAt(),
                p.getCompletedAt(),
                p.getDurationMs(),
                p.getReasonCode(),
                p.getAgentSessionId(),
                p.getAgentProvider());
    }
}
