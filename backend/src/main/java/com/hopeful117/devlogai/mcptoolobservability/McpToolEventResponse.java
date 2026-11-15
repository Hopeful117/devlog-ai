package com.hopeful117.devlogai.mcptoolobservability;

import java.time.Instant;
import java.util.UUID;

public record McpToolEventResponse(
        UUID id,
        String instanceId,
        String operationId,
        String toolName,
        String eventType,
        String status,
        int generation,
        Integer durationMs,
        String reasonCode,
        Instant occurredAt
) {}
