package com.hopeful117.devlogai.mcptoolobservability;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ActivityAlertService {

    private final McpToolEventRepository repository;

    @Transactional(readOnly = true)
    public ActivityAlertPageResponse listByProject(UUID projectId, int page, int size) {
        int clampedPage = Math.max(0, page);
        int clampedSize = Math.clamp(size, 1, 50);
        long total = repository.countAlertsByProject(projectId);
        List<McpToolEventRepository.ActivityAlertProjection> rows =
                repository.findAlertsByProject(projectId, clampedPage * clampedSize, clampedSize);
        return new ActivityAlertPageResponse(
                rows.stream().map(ActivityAlertService::toView).toList(),
                total,
                clampedPage,
                clampedSize);
    }

    @Transactional(readOnly = true)
    public ActivityAlertCountResponse countErrors(UUID projectId) {
        Instant windowStart = Instant.now().minus(1440, ChronoUnit.MINUTES);
        long count = repository.countErrorAlertsByProjectInWindow(projectId, windowStart);
        return new ActivityAlertCountResponse(count);
    }

    static ActivityAlertView toView(McpToolEventRepository.ActivityAlertProjection p) {
        boolean isFailed = "FAILED".equals(p.getStatus());
        boolean isProcessFailed = "MCP_PROCESS_FAILED".equals(p.getReasonCode());
        String severity = (isFailed || isProcessFailed) ? "ERROR" : "WARN";
        String eventType = isFailed ? "MCP_TOOL_FAILED" : "MCP_TOOL_INTERRUPTED";
        return new ActivityAlertView(
                p.getInstanceId() + "/" + p.getOperationId(),
                severity,
                eventType,
                p.getToolName(),
                p.getReasonCode(),
                p.getDurationMs(),
                p.getOccurredAt(),
                p.getInstanceId(),
                p.getOperationId(),
                p.getAgentSessionId(),
                p.getAgentProvider());
    }
}
