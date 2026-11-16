package com.hopeful117.devlogai.mcptoolobservability;

import com.hopeful117.devlogai.project.entity.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class McpSessionLifecycleEventService {
    private final McpSessionLifecycleEventRepository repository;

    @Transactional
    public void ingest(Project project, McpSessionLifecycleEventIngestRequest request) {
        Instant occurredAt;
        try {
            occurredAt = request.occurredAt() != null ? Instant.parse(request.occurredAt()) : Instant.now();
        } catch (Exception ignored) {
            occurredAt = Instant.now();
        }
        repository.insertIfAbsent(
                UUID.randomUUID(),
                project.getId(),
                request.agentSessionId(),
                request.eventType(),
                occurredAt,
                Instant.now(),
                request.agentProvider());
    }
}
