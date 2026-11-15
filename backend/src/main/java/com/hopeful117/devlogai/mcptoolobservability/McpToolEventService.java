package com.hopeful117.devlogai.mcptoolobservability;

import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
        McpToolEvent event = McpToolEvent.builder()
                .project(project)
                .instanceId(request.instanceId())
                .operationId(request.operationId())
                .toolName(request.toolName())
                .eventType(request.eventType())
                .status(request.status())
                .generation(request.generation() != null ? request.generation() : 0)
                .durationMs(request.durationMs())
                .reasonCode(request.reasonCode())
                .schemaVersion(request.schemaVersion())
                .occurredAt(occurredAt)
                .receivedAt(Instant.now())
                .build();
        try {
            repository.save(event);
        } catch (DataIntegrityViolationException ignored) {
            // concurrent identical push; UNIQUE constraint enforces idempotency
        }
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
