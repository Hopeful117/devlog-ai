package com.hopeful117.devlogai.mcptoolobservability;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record McpToolEventIngestRequest(
        @NotBlank @Size(max = 128) String projectSlug,
        @NotBlank @Size(max = 64)  String instanceId,
        @NotBlank @Size(max = 64)  String operationId,
        @Size(max = 128)           String toolName,
        @NotBlank @Size(max = 32)  String eventType,
        @NotBlank @Size(max = 16)  String status,
        @NotNull                   Integer generation,
        @NotBlank @Size(max = 64)  String schemaVersion,
        Integer durationMs,
        @Size(max = 64)            String reasonCode,
        String occurredAt,
        @Size(max = 128) String agentSessionId,
        @Size(max = 128) String agentProvider,
        @Size(max = 36)  String correlationId
) {}
