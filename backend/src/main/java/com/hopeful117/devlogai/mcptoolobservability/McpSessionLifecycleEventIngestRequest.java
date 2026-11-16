package com.hopeful117.devlogai.mcptoolobservability;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record McpSessionLifecycleEventIngestRequest(
        @NotBlank @Size(max = 128) String projectSlug,
        @NotBlank @Size(max = 128) String agentSessionId,
        @NotBlank @Size(max = 64)  String eventType,
        String occurredAt,
        @Size(max = 128) String agentProvider
) {}
