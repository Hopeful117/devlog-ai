package com.hopeful117.devlogai.mcptoolobservability;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentSessionService {
    private final McpToolEventRepository repository;

    @Transactional(readOnly = true)
    public AgentSessionPageResponse listByProject(UUID projectId, int page, int size) {
        int clampedPage = Math.max(0, page);
        int clampedSize = Math.clamp(size, 1, 50);
        long total = repository.countSessionsByProject(projectId);
        List<McpToolEventRepository.AgentSessionProjection> rows =
                repository.findSessionsByProject(projectId, clampedPage * clampedSize, clampedSize);
        return new AgentSessionPageResponse(
                rows.stream().map(AgentSessionService::toView).toList(),
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

    private static AgentSessionView toView(McpToolEventRepository.AgentSessionProjection p) {
        return new AgentSessionView(
                p.getAgentSessionId(),
                p.getAgentProvider(),
                p.getFirstObservedAt(),
                p.getLastObservedAt(),
                p.getOperationCount(),
                p.getRespondedCount(),
                p.getFailedCount(),
                p.getInterruptedCount(),
                p.getActiveOperationCount());
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
