package com.hopeful117.devlogai.mcptoolobservability;

import java.time.Instant;

public record McpOperationView(
        String instanceId,
        String operationId,
        String toolName,
        String status,
        int generation,
        Instant startedAt,
        Instant completedAt,
        Integer durationMs,
        String reasonCode
) {}
