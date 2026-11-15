package com.hopeful117.devlogai.mcptoolobservability;

import com.hopeful117.devlogai.project.entity.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class McpToolEventService {
    private final McpToolEventRepository repository;

    @Transactional
    public void ingest(Project project, McpToolEventIngestRequest request) {
        if (repository.existsByInstanceIdAndOperationIdAndEventType(
                request.instanceId(), request.operationId(), request.eventType())) {
            return;
        }
        Instant occurredAt;
        try {
            occurredAt = request.occurredAt() != null ? Instant.parse(request.occurredAt()) : Instant.now();
        } catch (Exception ignored) {
            occurredAt = Instant.now();
        }
        // ON CONFLICT DO NOTHING makes this idempotent for concurrent duplicate pushes without
        // relying on Hibernate's deferred flush, which would throw outside any try/catch.
        repository.insertIfAbsent(
                UUID.randomUUID(),
                project.getId(),
                request.instanceId(),
                request.operationId(),
                request.toolName(),
                request.eventType(),
                request.status(),
                request.generation() != null ? request.generation() : 0,
                request.durationMs(),
                request.reasonCode(),
                request.schemaVersion(),
                occurredAt,
                Instant.now());
    }

    @Transactional(readOnly = true)
    public McpToolEventPageResponse listByProject(UUID projectId, int page, int size) {
        int clampedPage = Math.max(0, page);
        int clampedSize = Math.clamp(size, 1, 50);
        Page<McpToolEvent> result = repository.findByProjectIdOrderByReceivedAtDesc(
                projectId, PageRequest.of(clampedPage, clampedSize));
        return new McpToolEventPageResponse(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getTotalElements(),
                clampedPage,
                clampedSize);
    }

    private McpToolEventResponse toResponse(McpToolEvent event) {
        return new McpToolEventResponse(
                event.getId(),
                event.getInstanceId(),
                event.getOperationId(),
                event.getToolName(),
                event.getEventType(),
                event.getStatus(),
                event.getGeneration(),
                event.getDurationMs(),
                event.getReasonCode(),
                event.getOccurredAt());
    }
}
