package com.hopeful117.devlogai.mcptoolobservability;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class McpOperationService {
    private final McpToolEventRepository repository;

    @Transactional(readOnly = true)
    public McpOperationPageResponse listByProject(UUID projectId, int page, int size) {
        int clampedPage = Math.max(0, page);
        int clampedSize = Math.clamp(size, 1, 50);
        long total = repository.countOperationsByProject(projectId);
        List<McpToolEventRepository.McpOperationProjection> rows =
                repository.findOperationsByProject(projectId, clampedPage * clampedSize, clampedSize);
        return new McpOperationPageResponse(
                rows.stream().map(McpOperationService::toView).toList(),
                total,
                clampedPage,
                clampedSize);
    }

    private static McpOperationView toView(McpToolEventRepository.McpOperationProjection p) {
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
